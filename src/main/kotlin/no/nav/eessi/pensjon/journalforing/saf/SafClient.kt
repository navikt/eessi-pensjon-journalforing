package no.nav.eessi.pensjon.journalforing.saf

import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
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

    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delayExpression = "@retrySafConfig.initialRetryMillis", delay = 10000L, maxDelay = 100000L, multiplier = 3.0),
        listeners  = ["retrySafLogger"]
    )
    fun hentJournalpost(journalpostId: String) {

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
                logger.debug(response.body.toString())

            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
            }
        }
    }
}
