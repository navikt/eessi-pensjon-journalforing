package no.nav.eessi.pensjon.listeners
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.slf4j.LoggerFactory

open class SedListenerBase(
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService
) {

    private val logger = LoggerFactory.getLogger(SedListenerBase::class.java)

    /**
     * Velger saktype fra enten bestemSak eller pensjonsinformasjon der det foreligger.
     */
    protected fun pensjonSakInformasjon(identifisertPerson: IdentifisertPerson?, bucType: BucType, saktypeFraSed: SakType?, alleSedIBuc: List<SED>): SakInformasjon? {

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

    protected fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, bucType: BucType): SakType? {
        if (bucType == BucType.P_BUC_02 && sakInformasjon != null && sakInformasjon.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET) return null
        else if (bucType == BucType.P_BUC_10 && saktypeFraSED == GJENLEV) return sakInformasjon?.sakType ?: saktypeFraSED
        else if (saktypeFraSED != null) return saktypeFraSED
        return sakInformasjon?.sakType
    }
}