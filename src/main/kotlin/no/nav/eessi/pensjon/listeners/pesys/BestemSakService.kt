package no.nav.eessi.pensjon.listeners.pesys

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class BestemSakService(private val klient: BestemSakKlient) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(BestemSakService::class.java) }

    /**
     * Funksjon for å hente ut saksinformasjon fra Pesys.
     *
     * @param aktoerId:     Aktøren sin ID
     * @param bucType:      Brukes for å sette riktig [Saktype]
     * @param innkommendeSakType:   Type ytelse det gjelder. I brukved følgende bucTyper [BucType]
     * P_BUC_02, P_BUC_05, P_BUC_10 eller R_BUC_02
     *
     * @return [SakInformasjon]
     */
    fun hentSakInformasjonViaBestemSak(
        aktoerId: String,
        bucType: BucType,
        saktypeFraSed: SakType? = null,
        identifisertPerson: IdentifisertPerson? = null
    ): List<SakInformasjon>? {
        val saktype = when (bucType) {
            P_BUC_01 -> ALDER
            P_BUC_03 -> UFOREP
            else -> bestemSaktypeFraSed(saktypeFraSed, identifisertPerson, bucType)?: return null  //Vi returnerer null her, da vi ikke har saksType. Sakstype er obligatorisk ved kall til bestemSak for å få Saksinformasjon.
        }
        logger.info("Prøver å finne saksInformasjon for bucType: $bucType, saksType: $saktype")
        return kallBestemSak(aktoerId, saktype)?.sakInformasjonListe.also { logger.info("BestemSak respons: ${it?.toJson()}") }
    }

    /**
    brukes kun ved for bruk av bestemsak for P_BUC_01 og P_BUC_03, da vi vet saktypen for disse bucTypene.
    OBS gjelder kun innkommende Seder
     */
    fun hentSakInformasjonViaBestemSakMottatt(
        aktoerId: String,
        bucType: BucType,
    ): List<SakInformasjon>? {
        logger.info("Oppretter sak i Pesys via Bestemsak for innkommende sed i $bucType")

        val saktype = when (bucType) {
            P_BUC_01 -> ALDER
            P_BUC_03 -> UFOREP
            else -> return null  //Vi returnerer null her, da vi ikke vet saksType
        }

        return kallBestemSak(aktoerId, saktype)?.sakInformasjonListe.also { logger.info("BestemSak respons for innkommende sed i $bucType: ${it?.toJson()}") }
    }

    private fun bestemSaktypeFraSed(saktypeFraSed: SakType?,identifisertPerson: IdentifisertPerson?, bucType: BucType ): SakType? {
        val saktype = identifisertPerson?.personRelasjon?.saktype
        logger.info("Saktype fra SED: ${saktypeFraSed?.name} identPersonYtelse: ${saktype?.name}")
        if (bucType == P_BUC_10 && saktypeFraSed == GJENLEV) {
            return saktype
        }
        return saktypeFraSed ?: saktype
    }

    private fun kallBestemSak(aktoerId: String, saktype: SakType): BestemSakResponse? {
        val randomId = UUID.randomUUID()
        val request = BestemSakRequest(aktoerId, saktype, randomId, randomId)

        return klient.kallBestemSak(request)
    }
}
