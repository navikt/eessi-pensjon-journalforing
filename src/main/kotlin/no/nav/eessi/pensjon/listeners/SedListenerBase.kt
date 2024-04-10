package no.nav.eessi.pensjon.listeners
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.slf4j.LoggerFactory

open class SedListenerBase(
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    private val gcpStorageService: GcpStorageService,
    private val euxService: EuxService
) {

    private val logger = LoggerFactory.getLogger(SedListenerBase::class.java)

    /**
     * Velger saktype fra enten bestemSak eller pensjonsinformasjon der det foreligger.
     */
    private fun pensjonSakInformasjon(identifisertPerson: IdentifisertPerson?, bucType: BucType, saktypeFraSed: SakType?, alleSedIBuc: List<SED>): SakInformasjon? {

        val aktoerId = identifisertPerson?.aktoerId ?: return null
            .also { logger.info("IdentifisertPerson mangler aktørId. Ikke i stand til å hente ut saktype fra bestemsak eller pensjonsinformasjon") }

        fagmodulService.hentPensjonSakFraPesys(aktoerId, alleSedIBuc).let { pensjonsinformasjon ->
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
        hendelseType: HendelseType
    ): SaksInfoSamlet {
        val saksIdFraSed = fagmodulService.hentSakIdFraSED(alleSedIBucList)
        val sakTypeFraSED = euxService.hentSaktypeType(sedHendelse, alleSedIBucList)
            .takeIf { bucType == BucType.P_BUC_10 || bucType == BucType.R_BUC_02 }

        val sakInformasjon = if (hendelseType == HendelseType.SENDT && gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)) null else {
            pensjonSakInformasjon(
                identifisertPerson,
                bucType,
                sakTypeFraSED,
                alleSedIBucList
            )
        }
        val saktype = populerSaktype(sakTypeFraSED, sakInformasjon, bucType)
        return SaksInfoSamlet(saksIdFraSed, sakInformasjon, saktype)
    }
}
