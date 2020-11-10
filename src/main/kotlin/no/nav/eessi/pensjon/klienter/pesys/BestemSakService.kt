package no.nav.eessi.pensjon.klienter.pesys

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.YtelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.util.*

@Service
class BestemSakService(private val klient: BestemSakKlient) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(BestemSakService::class.java) }

    /**
     * Funksjon for å hente ut saksinformasjon fra Pesys.
     *
     * @param aktoerId:     Aktøren sin ID
     * @param bucType:      Brukes for å sette riktig [YtelseType]
     * @param ytelseType:   Type ytelse det gjelder. Kun i bruk dersom [BucType] er P_BUC_02 eller R_BUC_02
     *
     * @return [SakInformasjon]
     */
    fun hentSakInformasjon(aktoerId: String, bucType: BucType, ytelsesType: YtelseType? = null): SakInformasjon? {
        val ytelseType = when (bucType) {
            BucType.P_BUC_01 -> YtelseType.ALDER
            BucType.P_BUC_02 -> ytelsesType ?: return null
            BucType.P_BUC_03 -> YtelseType.UFOREP
            BucType.R_BUC_02 -> ytelsesType!!
            else -> return null
        }

        logger.info("kallBestemSak aktoer: $aktoerId ytelseType: $ytelseType bucType: $bucType")

        val resp = kallBestemSak(aktoerId, ytelseType)
        if (resp != null && resp.sakInformasjonListe.size == 1) {
            return resp.sakInformasjonListe
                    .first()
                    .also { logger.info("resultat en sakInformasjon: ${it.toJson()}") }
        }

        logger.info("SakInformasjonListe er null eller større enn 1: ${resp?.sakInformasjonListe?.toJson()}")
        return null
    }

    private fun kallBestemSak(aktoerId: String, ytelseType: YtelseType): BestemSakResponse? {
        val randomId = UUID.randomUUID()
        val request = BestemSakRequest(aktoerId, ytelseType, randomId, randomId)

        return klient.kallBestemSak(request)
    }
}
