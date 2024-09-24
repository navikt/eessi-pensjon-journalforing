package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HentSakService(private val etterlatteService: EtterlatteService, private val gcpStorageService: GcpStorageService) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    /**
     * Henter en sak basert på sakid og/eller gjenny informasjon
     *
     * @param euxCaseId SaksID fra EUX.
     * @param sakIdFraSed SaksID fra SED (valgfri).
     * @param sakInformasjon Tilleggsinfo om saken (valgfri).
     * @param identifisertPersonFnr Fødselsnummer (valgfri).
     * @return Et `Sak`-objekt hvis:
     * 1. saken finnes i Gjenny.
     * 2. identifisert person fnr  eksisterer.
     * 3. det finnes gyldig Pesys-nummer i `sakInformasjon` eller `sakIdFraSed`.
     */
    fun hentSak(
        euxCaseId: String,
        sakIdFraSed: String? = null,
        sakInformasjon: SakInformasjon? = null,
        identifisertPersonFnr: Fodselsnummer? = null
    ): Sak? {

        if (euxCaseId == sakIdFraSed || euxCaseId == sakInformasjon?.sakId) {
            logger.error("SakIdFraSed: $sakIdFraSed eller sakId fra saksInformasjon: ${sakInformasjon?.sakId} er lik rinaSakId: $euxCaseId")
            return null
        }

        // 1. Joark oppretter ikke JP der det finnes sak, men mangler bruker
        if (identifisertPersonFnr == null) {
            logger.warn("Fnr mangler for rinaSakId: $euxCaseId, henter derfor ikke sak")
            return null
        }

        // 2. Sjekk for gjenny: fra etterlatte-api
        sakIdFraSed?.let { sakId ->
            etterlatteService.hentGjennySak(sakId).fold(
                onSuccess = { gjennySak -> return Sak("FAGSAK", gjennySak?.id.toString(), "EY") },
                onFailure = { error -> logger.warn("Finner ingen gjennySak for rinasakId: $euxCaseId, og sakID: $sakId") }
            )
        }

        // 3. Sjekk for gjenny: gcp
        if (gcpStorageService.gjennyFinnes(euxCaseId)) {
            val gjennySak = gcpStorageService.hentFraGjenny(euxCaseId)?.let { mapJsonToAny<GjennySak>(it) }
            return gjennySak?.sakId?.let { Sak("FAGSAK", it, "EY") }
        }

        // 4. Pesys nr fra pesys
        sakInformasjon?.sakId?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let {
            return Sak(
                "FAGSAK",
                it,
                "PP01"
            ).also { logger.info("Har funnet saksinformasjon fra pesys: $it, saksType:${sakInformasjon.sakType}, sakStatus:${sakInformasjon.sakStatus}") }
        }

        // 5. Pesys nr fra SED
        sakIdFraSed?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let {
            return Sak("FAGSAK", it, "PP01").also { logger.info("har funnet saksinformasjon fra SED: $it") }
        }

        logger.warn(
            """RinaID: $euxCaseId
            | sakIdFraSed: $sakIdFraSed eller sakId fra saksInformasjon: ${sakInformasjon?.sakId}
            | mangler verdi eller er ikke gyldig pesys nummer""".trimMargin()
        )
        return null
    }

    /**
     * @return true om første tall er 1 eller 2 (pesys saksid begynner på 1 eller 2) og at lengden er 8 siffer
     */
    fun String?.erGyldigPesysNummer(): Boolean {
        if(this.isNullOrEmpty()) return false
        return this.length == 8 && this.first() in listOf('1', '2') && this.all { it.isDigit() }
    }

}