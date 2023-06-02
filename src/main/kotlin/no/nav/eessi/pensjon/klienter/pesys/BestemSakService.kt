package no.nav.eessi.pensjon.klienter.pesys

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
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
    fun hentSakInformasjonViaBestemSak(aktoerId: String, bucType: BucType, innkommendeSakType: SakType? = null): SakInformasjon? {
        logger.info("Prøver å finne saksInformasjon for bucType: $bucType, saksType: $innkommendeSakType")
        val saktype = when (bucType) {
            P_BUC_01 -> ALDER
            P_BUC_02 -> innkommendeSakType ?: return null
            P_BUC_03 -> UFOREP
            P_BUC_05 -> innkommendeSakType ?: return null
            R_BUC_02 -> innkommendeSakType!!
            P_BUC_10 -> innkommendeSakType ?: return null
            else -> return null
        }

        val resp = kallBestemSak(aktoerId, saktype)
        if (resp != null && resp.sakInformasjonListe.size == 1) {
            return resp.sakInformasjonListe
                    .first()
                    .also { logger.info("BestemSak respons: ${it.toJson()}") }
        }

        logger.info("SakInformasjonListe er null eller større enn 1: ${resp?.sakInformasjonListe?.toJson()}")
        return null
    }

    private fun kallBestemSak(aktoerId: String, saktype: SakType): BestemSakResponse? {
        val randomId = UUID.randomUUID()
        val request = BestemSakRequest(aktoerId, saktype, randomId, randomId)

        return klient.kallBestemSak(request)
    }
}
