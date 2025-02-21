package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private const val FAGSAK = "FAGSAK"
private const val PP01 = "PP01"
private const val EY = "EY"

@Service
class HentSakService(private val etterlatteService: EtterlatteService, private val gcpStorageService: GcpStorageService) {

    private val logger = LoggerFactory.getLogger(HentSakService::class.java)

    /**
     * Henter en sak basert på sakid og/eller gjenny informasjon
     */
    fun hentSak(
        euxCaseId: String,
        sakIdFraSed: String? = null,
        sakInformasjon: SakInformasjon? = null,
        identifisertPersonFnr: Fodselsnummer? = null
    ): Sak? {
        if (euxCaseId == sakIdFraSed || euxCaseId == sakInformasjon?.sakId) {
            logger.error("SakIdFromSed: $sakIdFraSed eller sakId fra saksInformasjon: ${sakInformasjon?.sakId} er lik rinaSakId: $euxCaseId")
            return null
        }

        if (identifisertPersonFnr == null) {
            logger.warn("Fnr mangler for rinaSakId: $euxCaseId, henter derfor ikke sak")
            return null
        }

        return hentGjennySak(sakIdFraSed, euxCaseId).also { logger.info("hentSak: sjekker gjenny-API: rinasakId: $euxCaseId, og sakID: $sakIdFraSed") }
            ?: hentGjennyFraGCP(euxCaseId).also { logger.info("hentSak: sjekker GCP $euxCaseId") }
            ?: validerPesysSak(sakInformasjon, sakIdFraSed, euxCaseId).also { logger.info("hentSak: ser om :$sakIdFraSed er en gyldig pesys sakId") }
            ?: logOgReturnerNull(euxCaseId, sakIdFraSed, sakInformasjon)
    }

    private fun hentGjennySak(sakIdFromSed: String?, euxCaseId: String): Sak? {
        if (sakIdFromSed.isNullOrBlank()) {
            logger.warn("sakIdFromSed er tom eller mangler for euxCaseId: $euxCaseId")
            return null
        }

        etterlatteService.hentGjennySak(sakIdFromSed).fold(
            onSuccess = { gjennySak -> return Sak(FAGSAK, gjennySak?.id.toString(), EY) },
            onFailure = { logger.warn("Finner ingen gjennySak hos etterlatte for rinasakId: $euxCaseId, og sakID: $sakIdFromSed") }
        )

        return null
    }

    private fun hentGjennyFraGCP(euxCaseId: String): Sak? {
        return gcpStorageService.hentFraGjenny(euxCaseId)?.let { mapJsonToAny<GjennySak>(it) }?.sakId
            ?.let { Sak(FAGSAK, it, EY) }
    }

    private fun validerPesysSak(sakInfo: SakInformasjon?, sakIdFromSed: String?, euxCaseId: String): Sak? {
        // Hvis det finnes en gjenny sak, returner null
        if(gcpStorageService.hentFraGjenny(euxCaseId) != null) return null
        sakInfo?.sakId?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let { sakId ->
            return lagSak(sakId, sakInfo.sakType, sakInfo.sakStatus)
        }

        sakIdFromSed?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let {
            logger.info("Har funnet saksinformasjon fra SED: $it")
            return Sak(FAGSAK, it, PP01)
        }
        return null
    }

    private fun lagSak(sakId: String, sakType: SakType?, sakStatus: SakStatus?): Sak {
        logger.info("Har funnet saksinformasjon fra pesys: $sakId, saksType:$sakType, sakStatus:$sakStatus")
        return Sak(FAGSAK, sakId, PP01)
    }

    private fun logOgReturnerNull(euxCaseId: String, sakIdFromSed: String?, sakInfo: SakInformasjon?): Sak? {
        logger.warn(
            """RinaID: $euxCaseId
            | sakIdFromSed: $sakIdFromSed eller sakId fra saksInformasjon: ${sakInfo?.sakId}
            | mangler verdi eller er ikke gyldig pesys nummer""".trimMargin()
        )
        return null
    }

    fun String?.erGyldigPesysNummer(): Boolean {
        return this?.let { pesysSakId -> pesysSakId.length == 8 && pesysSakId.first() in listOf('1', '2') && pesysSakId.all { char -> char.isDigit() } }
            .also { logger.info("PesysSakId er gyldig") } ?: false
            .also { logger.info("PesysSakId er ikke gyldig ifht valideringsregler") }
    }
}