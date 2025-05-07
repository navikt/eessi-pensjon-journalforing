package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
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
import no.nav.eessi.pensjon.utils.toJson
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

    /** Velger saktype fra enten bestemSak eller pensjonsinformasjon der det finnes */
    private fun pensjonSakInformasjon(
        identifisertPerson: IdentifisertPerson?,
        bucType: BucType,
        saktypeFraSed: SakType?,
        alleSakIdFraSED: List<String>?,
        currentSed: SED?
    ): Pair<SakInformasjon?, List<SakInformasjon>>? ? {

        val aktoerId = identifisertPerson?.aktoerId ?: return null
            .also { logger.info("IdentifisertPerson mangler aktørId. Ikke i stand til å hente ut saktype fra bestemsak eller pensjonsinformasjon") }

        fagmodulService.hentPensjonSakFraPesys(aktoerId, alleSakIdFraSED, currentSed).let { pensjonsinformasjon ->
            if (pensjonsinformasjon?.first?.sakId != null || pensjonsinformasjon?.second?.isNotEmpty() == true) {
                logger.info("Velger sakType ${pensjonsinformasjon.first?.sakType} fra pensjonsinformasjon, for sakid: ${pensjonsinformasjon.first?.sakId}")
                return pensjonsinformasjon
            }
        }
        bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, saktypeFraSed, identifisertPerson).let {
            if (it?.sakType != null) {
                logger.info("Velger sakType ${it.sakType} fra bestemsak, for sak med sakid: ${it.sakId}")
                return Pair(it, emptyList())
            }
        }
        logger.info("Finner ingen sakType(fra bestemsak og pensjonsinformasjon) returnerer null.")
        return null
    }

    //TODO: Kan vi vurdere alle bucer som har mulighet for gjenlevende på samme måte som P_BUC_10 her?

    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, bucType: BucType): SakType? {
        return when {
            bucType == BucType.P_BUC_02 && sakInformasjon?.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET -> null
            bucType == BucType.P_BUC_10 && saktypeFraSED == GJENLEV -> sakInformasjon?.sakType ?: saktypeFraSED
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
            return SaksInfoSamlet(gjennySakId, null, null, emptyList())
        }

        val (sakIdFraSed, alleSakId) = fagmodulService.hentPesysSakIdFraSED(alleSedIBucList, currentSed) ?: Pair(null, emptyList())
        val sakTypeFraSED = euxService.hentSaktypeType(sedHendelse, alleSedIBucList)
            .takeIf { bucType == BucType.P_BUC_10 || bucType == BucType.R_BUC_02 }

        val sakInformasjonFraPesys = if (hendelseType == SENDT && gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)) null else {
            pensjonSakInformasjon(
                identifisertPerson,
                bucType,
                sakTypeFraSED,
                alleSakId,
                currentSed
            )
        }.also { logger.debug("SakInformasjon: $it") }
        val pesysSakListe = sakInformasjonFraPesys?.second ?: emptyList()
//        val pesysIDerFraSED = alleSakId

        // skal gi advarsel om vi har flere saker eller sed har pesys sakID som ikke matcher brukers pesys sakID
        val advarsel = if(sakInformasjonFraPesys?.second.isNullOrEmpty()) false else hentAdvarsel(alleSakId, sakInformasjonFraPesys?.second!!)

        //Dersom pesysSakid i Sed finnes, men sakiden ikke finnes i Pesys, så velger vi å journalføre manuelt
        if (sakIdFraSed != null && sakInformasjonFraPesys == null) {
            logger.warn("SakId fra Sed: ${sakIdFraSed}, pensjonsinformasjon returnerer null")
            return SaksInfoSamlet(null, null, sakTypeFraSED, alleSakId, advarsel)
        }

        //Dersom pesysSakid i innkommende Sed finnes så skal vi bruke pesysID fra pensjonsinformasjon og ikke pesysId fra sed
        if(sakInformasjonFraPesys?.first == null && pesysSakListe.size > 1 && hendelseType == MOTTATT) {
            logger.warn("SakId fra INNKOMMENDE, vi har flere svar fra pesys: ${pesysSakListe.toJson()}, men ingen treff mot saksid fra sed")
            return SaksInfoSamlet(null, null, sakTypeFraSED, alleSakId, advarsel)
        }

        if(pesysSakListe.isNotEmpty() && hendelseType == MOTTATT) {
            logger.warn("SakId fra INNKOMMENDE Sed: ${sakIdFraSed}, pensjonsinformasjon fra pesys: ${pesysSakListe.first().sakId}")
            val pesysPensjonInfromasjon = sakInformasjonFraPesys?.first ?: pesysSakListe.first()
            return SaksInfoSamlet(null, pesysPensjonInfromasjon, sakTypeFraSED, alleSakId, advarsel)
        }

        val saktypeFraSedEllerPesys = populerSaktype(sakTypeFraSED, sakInformasjonFraPesys?.first, bucType)
        return SaksInfoSamlet(sakIdFraSed, sakInformasjonFraPesys?.first, saktypeFraSedEllerPesys, alleSakId, advarsel)
    }

    /**
     * Henter ut advarsel dersom vi har pesys sakID i sed som ikke finnes i listen fra pesys
     */
    private fun hentAdvarsel(pesysIDerFraSED: List<String?>, pesysSakInformasjonListe: List<SakInformasjon>) : Boolean {
        val sakIdFraPesys = pesysSakInformasjonListe.map { it.sakId }
        return when {
            pesysIDerFraSED.isEmpty() -> false
            pesysIDerFraSED.any { sakIdFraPesys.contains(it) } -> false
            pesysIDerFraSED.none { it == pesysSakInformasjonListe.first().sakId } -> {
                logger.warn("Sed inneholder pesysSakId som vi ikke finner i listen fra pesys")
                true
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