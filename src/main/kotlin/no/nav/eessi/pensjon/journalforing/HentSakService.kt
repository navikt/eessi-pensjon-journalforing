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
     * Verifiserer at sakIdFraSed eller sakId fra sakInformasjon ikke er lik euxCaseId, hvis de er det, logger en feil og returnerer null.
     * Hvis identifisertPersonFnr er null, logger en advarsel og returnerer null.
     * Henter først sak fra gjenny-API ved hjelp av sakIdFraSed, hvis det ikke finnes, henter den fra GCP.
     * Hvis ingen gyldig sak finnes, validerer den mot Pesys-sak basert på sakInformasjon eller sakIdFraSed.
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

        // er sakid fra sed en gjenny sak? verifiserer mot etterlatte api
        if (sakIdFraSed != null && erVerifisertGjennySak(sakIdFraSed, euxCaseId)) {
            return Sak(FAGSAK, sakIdFraSed, EY)
                .also { logger.info("hentSak: fant gyldig gjenny sak i SED: $sakIdFraSed, for euxCaseId: $euxCaseId") }
        }

        // er lagret sakid fra gcp en gjenny sak? verifiserer mot etterlatte api
        gcpStorageService.hentFraGjenny(euxCaseId)?.let { gjennySakGCP ->
            mapJsonToAny<GjennySak>(gjennySakGCP).sakId?.let { sakIdGCP ->
                if(erVerifisertGjennySak(sakIdGCP, euxCaseId)) {
                    logger.info("hentSak: fant gyldig gjenny sak i GCP for euxCaseId: $euxCaseId")
                    return Sak(FAGSAK, sakIdGCP, EY)
                }
            }
            return null.also { logger.warn("hentSak: fant sak i GCP for euxCaseId: $euxCaseId, men klarte ikke å verifisere  sak: $gjennySakGCP mot etterlatte") }
        }

        return validerPesysSak(sakInformasjon, sakIdFraSed, euxCaseId)
            ?.also { logger.info("hentSak: ser om :$sakIdFraSed er en gyldig pesys sakId") }
        ?: logOgReturnerNull(euxCaseId, sakIdFraSed, sakInformasjon)
    }

    private fun erVerifisertGjennySak(sakIdFromSed: String?, euxCaseId: String): Boolean {
        if (sakIdFromSed.isNullOrBlank()) {
            logger.warn("sakIdFromSed er tom eller mangler for euxCaseId: $euxCaseId")
            return false
        }
        if (sakIdFromSed.erGjennySakNummer().not()) return false

        etterlatteService.hentGjennySak(sakIdFromSed).fold(
            onSuccess = { gjennySak -> return true },
            onFailure = { logger.warn("Finner ingen gjennySak hos etterlatte for rinasakId: $euxCaseId, og sakID: $sakIdFromSed") }
        )

        return false
    }

    private fun validerPesysSak(sakInfo: SakInformasjon?, sakIdFromSed: String?, euxCaseId: String): Sak? {
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

    private fun String?.erGjennySakNummer(): Boolean {
        return this?.let { gjennySakId ->
            if (gjennySakId.length in 4..5 && gjennySakId.all { char -> char.isDigit() }) {
                true.also { logger.info("GjennyNummer: $gjennySakId er gyldig") }
            } else {
                false.also { logger.info("GjennyNummer: $gjennySakId er ikke gyldig ifht valideringsregler") }
            }
        } ?: false
    }
}


