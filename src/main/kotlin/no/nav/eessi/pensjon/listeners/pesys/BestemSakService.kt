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
        val saksType = bestemSaktypeFraSed(saktypeFraSed, identifisertPerson, bucType)
        logger.info("Prøver å finne saksInformasjon for bucType: $bucType, saksType: $saksType")

        val saktype = when (bucType) {
            P_BUC_01 -> ALDER
            P_BUC_03 -> UFOREP
            else -> saksType?: return null  //Vi returnerer null her, da vi ikke har saksType. Sakstype er obligatorisk ved kall til bestemSak for å få Saksinformasjon.
        }

        return kallBestemSak(aktoerId, saktype)?.sakInformasjonListe.also { logger.info("BestemSak respons: ${it?.toJson()}") }
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
