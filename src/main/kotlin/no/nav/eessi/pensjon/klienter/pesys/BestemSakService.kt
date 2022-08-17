package no.nav.eessi.pensjon.klienter.pesys

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.Saktype
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
     * @param saktypeBUC02:   Type ytelse det gjelder. Kun i bruk dersom [BucType] er P_BUC_02 eller R_BUC_02
     *
     * @return [SakInformasjon]
     */
    fun hentSakInformasjon(aktoerId: String, bucType: BucType, saktypeBUC02: Saktype? = null): SakInformasjon? {
        val saktype = when (bucType) {
            BucType.P_BUC_01 -> Saktype.ALDER
            BucType.P_BUC_02 -> saktypeBUC02 ?: return null
            BucType.P_BUC_03 -> Saktype.UFOREP
            BucType.R_BUC_02 -> saktypeBUC02!!
            BucType.P_BUC_10 -> saktypeBUC02 ?: return null
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

    private fun kallBestemSak(aktoerId: String, saktype: Saktype): BestemSakResponse? {
        val randomId = UUID.randomUUID()
        val request = BestemSakRequest(aktoerId, saktype, randomId, randomId)

        return klient.kallBestemSak(request)
    }
}
