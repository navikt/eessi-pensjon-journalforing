package no.nav.eessi.pensjon.listeners
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personidentifisering.relasjoner.secureLog
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.kafka.support.Acknowledgment

abstract class SedListenerBase(
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    private val gcpStorageService: GcpStorageService,
    private val euxService: EuxService,
    private val profile: String
) : journalListener{

    private val logger = LoggerFactory.getLogger(SedListenerBase::class.java)

    /** Velger saktype fra enten bestemSak eller pensjonsinformasjon der det finnes */
    private fun pensjonSakInformasjon(
        identifisertPerson: IdentifisertPerson?,
        bucType: BucType,
        saktypeFraSed: SakType?,
        alleSedIBuc: List<SED>,
        currentSed: SED?
    ): SakInformasjon? {

        val aktoerId = identifisertPerson?.aktoerId ?: return null
            .also { logger.info("IdentifisertPerson mangler aktørId. Ikke i stand til å hente ut saktype fra bestemsak eller pensjonsinformasjon") }

        fagmodulService.hentPensjonSakFraPesys(aktoerId, alleSedIBuc, currentSed).let { pensjonsinformasjon ->
            if (pensjonsinformasjon?.sakType != null) {
                logger.info("Velger sakType ${pensjonsinformasjon.sakType} fra pensjonsinformasjon, for sakid: ${pensjonsinformasjon.sakId}")
                return pensjonsinformasjon
            }
        }
        bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, saktypeFraSed, identifisertPerson).let {
            if (it?.sakType != null) {
                logger.info("Velger sakType ${it.sakType} fra bestemsak, for sak med sakid: ${it.sakId}")
                return it
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
        if(erGjennysak) {
            val gjennySakId = fagmodulService.hentGjennySakIdFraSed(currentSed)
            if (gjennySakId != null) {
                oppdaterGjennySak(sedHendelse, gjennySakId).also { logger.info("Gjennysak oppdatert med sakId: $it") }
            }
            return SaksInfoSamlet(gjennySakId, null, null)
        }
        val saksIdFraSed = fagmodulService.hentSakIdFraSED(alleSedIBucList, currentSed)

        val sakTypeFraSED = euxService.hentSaktypeType(sedHendelse, alleSedIBucList)
            .takeIf { bucType == BucType.P_BUC_10 || bucType == BucType.R_BUC_02 }

        val sakInformasjon = if (hendelseType == HendelseType.SENDT && gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)) null else {
            pensjonSakInformasjon(
                identifisertPerson,
                bucType,
                sakTypeFraSED,
                alleSedIBucList,
                currentSed
            )
        }
        val saktypeFraSedEllerPesys = populerSaktype(sakTypeFraSED, sakInformasjon, bucType)
        return SaksInfoSamlet(saksIdFraSed, sakInformasjon, saktypeFraSedEllerPesys)
    }

    private fun oppdaterGjennySak(sedHendelse: SedHendelse, gjennysakFraSed: String) : String? {
        val gcpGjennysak = gcpStorageService.hentFraGjenny(sedHendelse.rinaSakId)?.let { mapJsonToAny<GjennySak>(it) }
        val gjennyFinnes = gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)

        return if (gjennyFinnes && gcpGjennysak?.sakId == null && gcpGjennysak != null) {
            gcpStorageService.oppdaterGjennysak(sedHendelse, gcpGjennysak, gjennysakFraSed)
        }
        else null
    }


    fun skippingOffsett(offset: Long, offsetsToSkip : List<Long>): Boolean {
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

        if (profile == "prod" && sedHendelse.avsenderId in TEST_DATA_SENDERS) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
        } else if ((sedRetning == HendelseType.SENDT || sedRetning == HendelseType.MOTTATT) && GyldigeHendelser.sendt(sedHendelse)) {
            logger.info("Godkjent sed: ${sedHendelse.sedId}, klar for behandling")

            val buc = euxService.hentBuc(sedHendelse.rinaSakId)
            secureLog.info("Buc: ${buc.id}, sensitive: ${buc.sensitive}, sensitiveCommitted: ${buc.sensitiveCommitted}")

            behandleSedHendelse(sedHendelse, buc)
        } else {
            logger.warn("SED: ${sedHendelse.sedType}, ${sedHendelse.rinaSakId} er ikke med i listen over gyldige hendelser")
        }

        acknowledgment.acknowledge()
    }
}

interface journalListener {
    fun behandleSedHendelse(sedHendelse: SedHendelse, buc: Buc)
}