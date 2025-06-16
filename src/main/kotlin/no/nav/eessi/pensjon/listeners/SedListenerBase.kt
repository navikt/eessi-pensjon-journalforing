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
        sakIdsFraCurrentSed: List<String>?,
        retning: HendelseType
    ): Pair<SakInformasjon?, List<SakInformasjon>>?? {

        val aktoerId = identifisertPerson?.aktoerId ?: return null.also {
            logger.info("IdentifisertPerson mangler aktørId. Ikke i stand til å hente ut saktype fra bestemsak eller pensjonsinformasjon")
        }
        val sakFraPenInfo = fagmodulService.hentPesysSakId(aktoerId, bucType).takeIf { it?.isNotEmpty() == true }
        val sakInformasjon = hentSakInformasjon(sakFraPenInfo, bucType, saktypeFraSed, aktoerId, identifisertPerson, retning)

        if (sakInformasjon.isNullOrEmpty()) {
            return null.also {
                logger.info("Ingen saktype fra pensjonsinformasjon eller bestemsak. Ingen saktype å velge mellom.")
            }
        }
        if (sakIdsFraAlleSed.isNullOrEmpty()) return Pair(null, sakInformasjon)

        // Ser først om vi har treff fre sakliste fra current sed
        sakIdsFraCurrentSed?.asSequence()
            ?.mapNotNull { sakId -> fagmodulService.hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, sakInformasjon) }
            ?.find { it.first != null }
            ?.let { return Pair(it.first, it.second) }

        // Ser så om vi har treff fra sakliste fra alle sed
        val collectedResults = sakIdsFraAlleSed.mapNotNull { sakId ->
            fagmodulService.hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, sakInformasjon)
        }

        return collectedResults.find { it.first != null }
            ?: collectedResults.find { sakIdsFraAlleSed.contains(it.first?.sakId) }
            ?: collectedResults.firstOrNull()
    }

    private fun hentSakInformasjon(
        sakFraPenInfo: List<SakInformasjon>?,
        bucType: BucType,
        saktypeFraSed: SakType?,
        aktoerId: String,
        identifisertPerson: IdentifisertPerson,
        retning: HendelseType
    ): List<SakInformasjon>? {
        val sakInformasjon = sakFraPenInfo?.let { list ->
            when {
                bucType == P_BUC_01 && list.any { it.sakType == ALDER } -> list.filter { it.sakType == ALDER }
                bucType == P_BUC_03 && list.any { it.sakType == UFOREP } -> list.filter { it.sakType == UFOREP }
                saktypeFraSed == null && bucType == P_BUC_01 && list.any { it.sakType == UFOREP } -> bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, ALDER, identifisertPerson)
                saktypeFraSed == null && bucType == P_BUC_03 && list.any { it.sakType == ALDER } -> bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, UFOREP, identifisertPerson)
                else -> list
            }
        } ?: saktypeFraSed?.let { infoBestemSak ->
            bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, infoBestemSak, identifisertPerson).also {
                logger.info("Mangler sakinfo fra PenInfo, men har saktype fra SED, benytter bestemSak: $it")
            }
        } ?: if ((bucType == P_BUC_01 || bucType == P_BUC_03) && retning == MOTTATT) {
            bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType).also {
                logger.info("Mangler sakinfo og saktype, men buctype er ${bucType}og retning er MOTTATT, benytter bestemSak: $it")
            }
        } else null
        return sakInformasjon
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

        // henter saker fra PenInfo og bestemSak
        val (saksIdFraSed, sakIdFraBuc) = fagmodulService.hentPesysSakIdFraSED(alleSedIBucList, currentSed)
            ?: (emptyList<String>() to emptyList())

        val sakTypeFraSED = if (bucType == P_BUC_10 || bucType == R_BUC_02) {
            euxService.hentSaktypeType(sedHendelse, alleSedIBucList)
        } else null

        // svar fra PenInfo eller bestemSak som matcher SED (sakFraPesysSomMatcherSed), samt svar som ikke matcher (sakerFraPesys)
        val (sakFraPesysSomMatcherSed, sakerFraPesys) = when {
            hendelseType == SENDT && gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId) -> null to emptyList()
            else -> pensjonSakInformasjon(
                identifisertPerson = identifisertPerson,
                bucType = bucType,
                saktypeFraSed = sakTypeFraSED,
                sakIdsFraAlleSed = sakIdFraBuc,
                sakIdsFraCurrentSed = saksIdFraSed,
                retning = hendelseType
            ).also { logger.info("SakInformasjon: $it") } ?: (null to emptyList())
        }

        // advarsel for oppgave
        val advarsel = hentAdvarsel(pesysIDerFraSED = saksIdFraSed, pesysSakInformasjonListe = sakerFraPesys, hendesesType = hendelseType, match = sakFraPesysSomMatcherSed != null)

        val saktypeFraSedEllerPesys = populerSaktype(
            saktypeFraSED = sakTypeFraSED,
            sakInformasjon = sakFraPesysSomMatcherSed ?: sakerFraPesys.firstOrNull(),
            bucType = bucType
        )

        val harSakIdFraSed = saksIdFraSed.isNotEmpty()          // har vi sakID fra SED?
        val match = sakFraPesysSomMatcherSed != null            // matcher sakID fra SED med sakID fra PESYS?
        val harSvarFraPen = sakerFraPesys.isNotEmpty()          // har vi svar fra PenInfo eller bestemSak?
        val flereSakerfraPenInfo = sakerFraPesys.size > 1       // finnes det flere saker i svar fra PenInfo eller bestemSak?

        val sakInformasjonFraPesysFirst = sakerFraPesys.firstOrNull()  // første sak i listen fra PESYS, hvis den finnes

        when (hendelseType) {
            MOTTATT -> {
                //0. TODO: Skal denne beskrives mer? ev legges til en av de andre ?
                if (!harSakIdFraSed && match) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also { logScenario("0", hendelseType, advarsel, it) }
                }

                //1.
                if (harSakIdFraSed && !match && harSvarFraPen && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("1", hendelseType, advarsel, it)
                    }
                }

                //2.
                if (!harSakIdFraSed && !match && harSvarFraPen && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(saksIdFraSed.toString(), sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("2", hendelseType, advarsel, it)
                    }
                }

                //3.
                if (harSakIdFraSed && match && harSvarFraPen == true) {
                    return SaksInfoSamlet(sakFraPesysSomMatcherSed?.sakId, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("3", hendelseType, advarsel, it)
                    }
                }

                //4.
                if (harSakIdFraSed && !match && harSvarFraPen == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("4", hendelseType, advarsel, it)
                    }
                }

                //5.
                if (!harSakIdFraSed && !match && harSvarFraPen == false && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("5", hendelseType, advarsel, it)
                    }
                }

                //6.
                if (!harSakIdFraSed && !match && harSvarFraPen == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("6", hendelseType, advarsel, it)
                    }
                }

                return SaksInfoSamlet(saksIdFraSed.firstOrNull(), null, saktypeFraSedEllerPesys, advarsel).also {
                    logScenario("Default inn", hendelseType, advarsel, it)
                }
            }

            SENDT -> {
                //7.
                if (!harSakIdFraSed && !match && harSvarFraPen == false && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("7", hendelseType, advarsel, it)
                    }
                }

                //8.
                if (harSakIdFraSed && !match && harSvarFraPen == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, null, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("8", hendelseType, advarsel, it)
                    }
                }

                //9.
                if (!harSakIdFraSed && !match && harSvarFraPen == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("9", hendelseType, advarsel, it)
                    }
                }

                //10.
                if (!harSakIdFraSed && !match && harSvarFraPen == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(null, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("10", hendelseType, advarsel, it)
                    }
                }

                //11.
                if (harSakIdFraSed && match && harSvarFraPen == true && flereSakerfraPenInfo) {
                    return SaksInfoSamlet(sakFraPesysSomMatcherSed?.sakId, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("11", hendelseType, advarsel, it)
                    }
                }

                //12.
                if (harSakIdFraSed && match && harSvarFraPen == true && !flereSakerfraPenInfo) {
                    return SaksInfoSamlet(sakFraPesysSomMatcherSed?.sakId, sakInformasjonFraPesysFirst, saktypeFraSedEllerPesys, advarsel).also {
                        logScenario("12", hendelseType, advarsel, it)
                    }
                }
                return SaksInfoSamlet(saksIdFraSed.firstOrNull(), sakFraPesysSomMatcherSed ?: sakerFraPesys.firstOrNull(), saktypeFraSedEllerPesys, advarsel).also {
                    logScenario("Default ut", hendelseType, advarsel, it)
                }
            }
        }
    }

    fun logScenario(scenario: String, hendelseType: HendelseType, advarsel: Boolean, samlet: SaksInfoSamlet){
        logger.info("Scenario: $scenario, hendelseType: $hendelseType, advarsel: $advarsel, sakId fra sed: $samlet")
    }

    /**
     * Henter ut advarsel dersom vi har pesys sakID i sed som ikke finnes i listen fra pesys
     */
    private fun hentAdvarsel(
        pesysIDerFraSED: List<String?>,
        pesysSakInformasjonListe: List<SakInformasjon>,
        hendesesType: HendelseType,
        match: Boolean
    ): Boolean {
        return when {
            match -> false.also { logger.info("Ingen advarsel; sakID fra sed matcher sakID fra pesys") }
            pesysIDerFraSED.isNotEmpty() && pesysSakInformasjonListe.isNotEmpty() && hendesesType == SENDT && !match -> true.also {
                logger.warn(
                    "Ingen match ved flere sakId i SED, men finner én sak fra pesys; Advarsel"
                )
            }

            pesysSakInformasjonListe.size == 1 && hendesesType == SENDT -> false.also { logger.info("Kun én sak fra pesys; ingen advarsel") }
            pesysIDerFraSED.isEmpty() && pesysSakInformasjonListe.isEmpty() -> false.also { logger.info("Ingen sakid i sed eller svar fra pensjonsinformasjon") }
            pesysIDerFraSED.isNotEmpty() && pesysSakInformasjonListe.isNotEmpty() && hendesesType == MOTTATT && !match -> true // Advarsel, scenario 1 og 4
                .also { logger.warn("Sed inneholder pesysSakId som vi ikke finner i listen fra pesys; ADVARSEL") }
            else -> false
        }
    }

    private fun oppdaterGjennySak(sedHendelse: SedHendelse, gjennysakFraSed: String): String? {
        val gcpGjennysak = gcpStorageService.hentFraGjenny(sedHendelse.rinaSakId)?.let { mapJsonToAny<GjennySak>(it) }
        val gjennyFinnes = gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)

        if(gjennysakFraSed.length != 5 || gjennysakFraSed.any { !it.isDigit() }) {
            logger.error("SakId må være gjenny sak med 5 tegn; mottok: $gjennysakFraSed")
            return null
        }

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
            logger.info("SedType: ${sedHendelse.sedType}, sedID: ${sedHendelse.sedId}, buctype: ${sedHendelse.bucType}, er ikke med i listen over gyldige hendelser")
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
            if (bucType == null || sedType == null) return //ikke nødvendig å logge null verdier

            Metrics.counter("behandled_sed_${retning.lowercase()}_gyldig", "bucSed", "${bucType}, $sedType").increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding", e)
        }
    }

    private fun metricForUgyldigSed(retning: String, bucType: BucType?, sedType: SedType?) {
        try {
            if (bucType == null || sedType == null) return //ikke nødvendig å logge null verdier

            Metrics.counter("behandled_sed_${retning.lowercase()}_ugyldig", "bucSed", "${bucType}, $sedType")
                .increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding", e)
        }
    }

}


interface journalListener {
    fun behandleSedHendelse(sedHendelse: SedHendelse, buc: Buc)
}