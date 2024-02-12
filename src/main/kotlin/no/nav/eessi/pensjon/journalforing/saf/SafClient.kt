package no.nav.eessi.pensjon.journalforing.saf

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.journalforing.JournalpostResponse
import no.nav.eessi.pensjon.journalforing.OpprettJournalPostResponse
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
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

    fun hentJournalpost(journalpostId: String) : JournalpostResponse? {

        return HentDokumentInnhold.measure {
            try {
                logger.info("Henter dokumentinnhold for journalpostId:$journalpostId")

                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val response = safGraphQlOidcRestTemplate.exchange(
                    "/",
                    HttpMethod.POST,
                    HttpEntity(SafRequest(journalpostId).toJson(), headers),
                    String::class.java
                )
                val journalPostReponse = mapJsonToAny<Response>(response.body!!).takeIf { true }
                return@measure journalPostReponse?.data?.journalpost

            }  catch (ce: HttpClientErrorException) {
                if(ce.statusCode == HttpStatus.FORBIDDEN) {
                    logger.error("En feil oppstod under henting av dokument metadata fra SAF for ikke tilgang", ce)
                }
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}, ${ce.statusCode}")
            } catch (se: HttpServerErrorException) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
            }
            null
        }
    }

    data class Data(
        @JsonProperty("journalpost") val journalpost: JournalpostResponse
    )

    data class Response(
        @JsonProperty("data") val data: Data
    )
}
