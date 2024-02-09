package no.nav.eessi.pensjon.journalforing.saf

import no.nav.eessi.pensjon.gcp.JournalpostDetaljer
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SafClient(private val safGraphQlOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(SafClient::class.java)

    private lateinit var HentDokumentMetadata: MetricsHelper.Metric
    private lateinit var HentDokumentInnhold: MetricsHelper.Metric
    private lateinit var HentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    init {
        HentDokumentMetadata = metricsHelper.init("HentDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentDokumentInnhold = metricsHelper.init("HentDokumentInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED))
        HentRinaSakIderFraDokumentMetadata = metricsHelper.init("HentRinaSakIderFraDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    fun hentJournalpost(journalpostId: String) : JournalpostDetaljer? {

        return HentDokumentInnhold.measure {
            try {
                logger.info("Henter dokumentinnhold for journalpostId:$journalpostId")

                val path = "/$journalpostId"
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val response = safGraphQlOidcRestTemplate.exchange(
                    path,
                    HttpMethod.POST,
                    HttpEntity(SafRequest(journalpostId), headers),
                    String::class.java
                )
                response.body?.let { mapJsonToAny<JournalpostDetaljer>(it) }

            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
            }
            null
        }
    }
}
