package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personidentifisering.relasjoner.secureLog
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.support.Acknowledgment

abstract class SedListenerBase(
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    private val gcpStorageService: GcpStorageService,
    private val euxService: EuxService,
    private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) : journalListener {

    private val logger = LoggerFactory.getLogger(SedListenerBase::class.java)
    private var gyldigeSed: MetricsHelper.Metric = metricsHelper.init("gyldigeSed")
    private var ugyldigeSed: MetricsHelper.Metric = metricsHelper.init("ugyldigeSed")

    /** Velger SakInformasjon fra enten bestemSak eller pensjonsinformasjon der det finnes */
    fun pensjonSakInformasjon(
        identifisertPerson: IdentifisertPerson?,
        bucType: BucType,
        saktypeFraSed: SakType?,
        sakIdsFraAlleSed: List<String>?,
        sakIdsFraCurrentSed: List<String> ?
    ): Pair<SakInformasjon?, List<SakInformasjon>>?? {

        val aktoerId = identifisertPerson?.aktoerId ?: return null
            .also { logger.info("IdentifisertPerson mangler aktørId. Ikke i stand til å hente ut saktype fra bestemsak eller pensjonsinformasjon") }

        val sakFraPenInfo = fagmodulService.hentPesysSakId(aktoerId, bucType).takeIf { it?.isNotEmpty() == true }

        val sakInformasjon = sakFraPenInfo?.let { listFraPenInfo ->
            when {
                bucType == P_BUC_01 && listFraPenInfo.any { sak -> sak.sakType == ALDER } -> listFraPenInfo.filter { it.sakType == ALDER }
                bucType == P_BUC_03 && listFraPenInfo.any { sak -> sak.sakType == UFOREP } -> listFraPenInfo.filter { it.sakType == UFOREP }
                bucType == P_BUC_01 && listFraPenInfo.any { sak -> sak.sakType == UFOREP } && saktypeFraSed == null->
                    bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, ALDER, identifisertPerson)
                bucType == P_BUC_03 && listFraPenInfo.any { sak -> sak.sakType == ALDER } && saktypeFraSed == null->
                    bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, UFOREP, identifisertPerson)
                else -> listFraPenInfo
            }
        } ?: saktypeFraSed?.let {
            bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, it, identifisertPerson)
        }

        if (sakInformasjon.isNullOrEmpty()) {
            logger.info("Ingen saktype fra pensjonsinformasjon eller bestemsak. Ingen saktype å velge mellom.")
            return null
        }
        if (sakIdsFraAlleSed.isNullOrEmpty()) return Pair(null, sakInformasjon)

        // Ser først om vi har treff fre sakliste fra current sed
        sakIdsFraCurrentSed?.mapNotNull { sakId ->
            fagmodulService.hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, sakInformasjon)
        }?.find { it.first != null }?.let {
            return Pair(it.first, it.second)
        }

        // Ser så om vi har treff fra sakliste fra alle sed
        val collectedResults = sakIdsFraAlleSed.mapNotNull { sakId ->
            fagmodulService.hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, sakInformasjon)
        }

        return collectedResults.find { it.first != null }
            ?: collectedResults.find { sakIdsFraAlleSed.contains(it.first?.sakId) }
            ?: collectedResults.firstOrNull()
    }

    //TODO: Kan vi vurdere alle bucer som har mulighet for gjenlevende på samme måte som P_BUC_10 her?

    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, bucType: BucType): SakType? {
        return when {
            bucType == P_BUC_03 -> UFOREP
            bucType == P_BUC_01 -> ALDER
            bucType == P_BUC_02 && sakInformasjon?.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET -> null
            bucType == P_BUC_10 && saktypeFraSED == GJENLEV -> sakInformasjon?.sakType ?: saktypeFraSED
            else -> saktypeFraSED ?: sakInformasjon?.sakType
        }
    }

    fun hentSaksInformasjonForEessi(
        alleSedIBucList: List<SED>,
        sedHendelse: SedHendelse,
        bucType: BucType,
        identifisertPerson: IdentifisertPDLPerson?,
        hendelseType: HendelseType,
        currentSed: SED?
    ): SaksInfoSamlet {
        val erGjennysak = gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)
        if (erGjennysak) {
            val gjennySakId = fagmodulService.hentGjennySakIdFraSed(currentSed)
            if (gjennySakId != null) {
                oppdaterGjennySak(sedHendelse, gjennySakId).also { logger.info("Gjennysak oppdatert med sakId: $it") }
            }
            return SaksInfoSamlet(gjennySakId)
        }

        val (saksIdFraSed, alleSakId) = fagmodulService.hentPesysSakIdFraSED(alleSedIBucList, currentSed) ?: Pair(emptyList(), emptyList())
        val sakTypeFraSED = euxService.hentSaktypeType(sedHendelse, alleSedIBucList)
            .takeIf { bucType == P_BUC_10 || bucType == R_BUC_02 }

        val sakInformasjonFraPesys = if (hendelseType == SENDT && gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)) null else {
            pensjonSakInformasjon(
                identifisertPerson,
                bucType,
                sakTypeFraSED,
                alleSakId,
                saksIdFraSed
            )
        }.also { logger.debug("SakInformasjon: $it") }
        val listeOverSakerPesys = sakInformasjonFraPesys?.second ?: emptyList()
        val sakFraPesysSomMatcherSed = sakInformasjonFraPesys?.first

        // skal gi advarsel om vi har flere saker eller sed har pesys sakID som ikke matcher brukers pesys sakID
        val advarsel = hentAdvarsel(alleSakId, listeOverSakerPesys, hendelseType, sakFraPesysSomMatcherSed != null)
        val saktypeFraSedEllerPesys = populerSaktype(sakTypeFraSED, sakFraPesysSomMatcherSed ?: listeOverSakerPesys.firstOrNull(), bucType)

        val pesysSakIdISED = saksIdFraSed.isNotEmpty()
        val match = sakFraPesysSomMatcherSed != null
        val svarFraPenInfo = sakInformasjonFraPesys?.second?.isNotEmpty()
        val flereSakerfraPenInfo = (svarFraPenInfo == true && sakInformasjonFraPesys.second.size > 1)

        val sakInformasjonFraPesysFirst = sakInformasjonFraPesys?.second?.firstOrNull()
        when (hendelseType) {
            MOTTATT -> {
                //0. TODO: Skal denne beskrives mer? ev legges til en av de andre ?
                if(!pesysSakIdISED && match) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("0", hendelseType, advarsel, it) }
                }

                //1.
                if(pesysSakIdISED && !match && svarFraPenInfo == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also { logScenario("1", hendelseType, advarsel, it) }
                }

                //2.
                if(!pesysSakIdISED && !match && svarFraPenInfo == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(saksIdFraSed.toString(), sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("2", hendelseType, advarsel, it) }
                }

                //3.
                if(pesysSakIdISED && match && svarFraPenInfo == true) {
                    return SaksInfoSamlet(sakFraPesysSomMatcherSed?.sakId, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("3", hendelseType, advarsel, it) }
                }

                //4.
                if(pesysSakIdISED && !match && svarFraPenInfo == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also { logScenario("4", hendelseType, advarsel, it) }
                }

                //5.
                if(!pesysSakIdISED && !match && svarFraPenInfo == false && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also { logScenario("5", hendelseType, advarsel, it) }
                }

                //6.
                if(!pesysSakIdISED && !match && svarFraPenInfo == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("5", hendelseType, advarsel, it) }
                }

                return SaksInfoSamlet(saksIdFraSed.firstOrNull(), null, saktypeFraSedEllerPesys, advarsel).also { logScenario("Default inn", hendelseType, advarsel, it) }
            }

            SENDT -> {
                //7.
                if(!pesysSakIdISED && !match && svarFraPenInfo == false && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("7", hendelseType, advarsel, it) }
                }

                //8.
                if(pesysSakIdISED && !match && svarFraPenInfo == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also { logScenario("8", hendelseType, advarsel, it) }
                }

                //9.
                if(!pesysSakIdISED && !match && svarFraPenInfo == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("9", hendelseType, advarsel, it) }
                }

                //10.
                if(!pesysSakIdISED && !match && svarFraPenInfo == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("10", hendelseType, advarsel, it) }
                }

                //11.
                if(pesysSakIdISED && match && svarFraPenInfo == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(sakFraPesysSomMatcherSed?.sakId, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("11", hendelseType, advarsel, it) }
                }

                //12.
                if(pesysSakIdISED && match && svarFraPenInfo == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(sakFraPesysSomMatcherSed?.sakId, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("12", hendelseType, advarsel, it) }
                }
                return SaksInfoSamlet(saksIdFraSed.firstOrNull(), sakFraPesysSomMatcherSed?: listeOverSakerPesys.firstOrNull(), saktypeFraSedEllerPesys, advarsel).also { logScenario("Default ut", hendelseType, advarsel, it) }
            }
        }
    }

    fun logScenario(string: String, hendelseType: HendelseType, advarsel: Boolean, samlet: SaksInfoSamlet){
        logger.debug("Scenario: $string, hendelseType: $hendelseType, advarsel: $advarsel, sakId fra sed: $samlet")
    }

    /**
     * Henter ut advarsel dersom vi har pesys sakID i sed som ikke finnes i listen fra pesys
     */
    private fun hentAdvarsel(
        pesysIDerFraSED: List<String?>,
        pesysSakInformasjonListe: List<SakInformasjon>,
        hendesesType: HendelseType,
        match: Boolean
    ) : Boolean {
        return when {
            match -> false .also { logger.info("Ingen advarsel; sakID fra sed matcher sakID fra pesys") }
            pesysIDerFraSED.isNotEmpty() && pesysSakInformasjonListe.isNotEmpty() && hendesesType == SENDT && !match -> true .also { logger.warn("Ingen match ved flere sakId i SED, men finner én sak fra pesys; Advarsel") }
            pesysSakInformasjonListe.size == 1 && hendesesType == SENDT -> false .also { logger.info("Kun én sak fra pesys; ingen advarsel") }
            pesysIDerFraSED.isEmpty()  && pesysSakInformasjonListe.isEmpty()-> false  .also { logger.info("Ingen sakid i sed eller svar fra pensjonsinformasjon") }
            hendesesType == MOTTATT && pesysIDerFraSED.none { pensjonsinformasjon -> pensjonsinformasjon in pesysSakInformasjonListe.map { it.sakId } } -> {
                if (pesysSakInformasjonListe.isNotEmpty() && pesysIDerFraSED.isEmpty()) {
                    false
                } else {
                    logger.warn("Sed inneholder pesysSakId som vi ikke finner i listen fra pesys")
                    true
                }
            }
            else -> false
        }
    }

    private fun oppdaterGjennySak(sedHendelse: SedHendelse, gjennysakFraSed: String): String? {
        val gcpGjennysak = gcpStorageService.hentFraGjenny(sedHendelse.rinaSakId)?.let { mapJsonToAny<GjennySak>(it) }
        val gjennyFinnes = gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)

        return if (gjennyFinnes && gcpGjennysak?.sakId == null && gcpGjennysak != null) {
            gcpStorageService.oppdaterGjennysak(sedHendelse, gcpGjennysak, gjennysakFraSed)
        } else null
    }


    fun skippingOffsett(offset: Long, offsetsToSkip: List<Long>): Boolean {
        return if (offset !in offsetsToSkip) {
            false
        } else {
            logger.warn("Offset ligger i listen over unntak, hopper over denne: $offset")
            true
        }
    }

    private val TEST_DATA_SENDERS = listOf("NO:NAVAT05", "NO:NAVAT07")

    fun behandleHendelse(hendelse: String, sedRetning: HendelseType, acknowledgment: Acknowledgment) {
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val gyldigeSendteHendelser = (sedRetning == SENDT) && GyldigeHendelser.sendt(sedHendelse)
        val gyldigeMottatteHendelser = (sedRetning == MOTTATT) && GyldigeHendelser.mottatt(sedHendelse)

        if (profile == "prod" && sedHendelse.avsenderId in TEST_DATA_SENDERS) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
        } else if (gyldigeSendteHendelser || gyldigeMottatteHendelser) {
            logger.info("Gyldig sed: ${sedHendelse.sedId}, klar for behandling")

            val buc = euxService.hentBuc(sedHendelse.rinaSakId)
            secureLog.info("Buc: ${buc.id}, sedid: ${sedHendelse.sedId} sensitive: ${buc.sensitive}, sensitiveCommitted: ${buc.sensitiveCommitted}")
            behandleSedHendelse(sedHendelse, buc)

            metricForGyldigSed(sedRetning.toString(), sedHendelse.bucType, sedHendelse.sedType)
        } else {
            logger.warn("SED: ${sedHendelse.sedType}, ${sedHendelse.sedId} er ikke med i listen over gyldige hendelser")
            val sedSendt = (sedRetning == SENDT && sedHendelse.bucType in GyldigeHendelser.gyldigUtgaaendeBucType)
            val sedMottatt = (sedRetning == MOTTATT && sedHendelse.bucType in GyldigeHendelser.gyldigeInnkommendeBucTyper)

            if (sedMottatt || sedSendt) {
                throw RuntimeException("Sed ${sedHendelse.sedType}, buc: ${sedHendelse.bucType} burde vært håndtert")
            }
            metricForUgyldigSed(sedRetning.toString(), sedHendelse.bucType, sedHendelse.sedType)
        }
        acknowledgment.acknowledge()
    }

    private fun metricForGyldigSed(retning: String, bucType: BucType?, sedType: SedType?) {
        try {
            if(bucType == null || sedType == null) return //ikke nødvendig å logge null verdier

            Metrics.counter("behandled_sed_${retning.lowercase()}_gyldig", "bucSed", "${bucType}, $sedType").increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding", e)
        }
    }

    private fun metricForUgyldigSed(retning: String, bucType: BucType?, sedType: SedType?) {
        try {
            if(bucType == null || sedType == null) return //ikke nødvendig å logge null verdier

            Metrics.counter("behandled_sed_${retning.lowercase()}_ugyldig", "bucSed", "${bucType}, $sedType").increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding", e)
        }
    }

}


interface journalListener {
    fun behandleSedHendelse(sedHendelse: SedHendelse, buc: Buc)
}