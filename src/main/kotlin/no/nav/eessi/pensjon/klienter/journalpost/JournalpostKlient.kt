package no.nav.eessi.pensjon.klienter.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import javax.annotation.PostConstruct

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class JournalpostKlient(
        private val journalpostOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostKlient::class.java) }
    private val mapper = jacksonObjectMapper()

    private lateinit var opprettjournalpost: MetricsHelper.Metric
    private lateinit var oppdaterDistribusjonsinfo: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        opprettjournalpost = metricsHelper.init("opprettjournalpost")
        oppdaterDistribusjonsinfo = metricsHelper.init("oppdaterDistribusjonsinfo")
    }

    /**
     * TODO:
     **/
    fun opprettJournalpost(request: OpprettJournalpostRequest, forsokFerdigstill: Boolean): OpprettJournalPostResponse? {
        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"

        return opprettjournalpost.measure {
            return@measure try {
                logger.info("Kaller Joark for å generere en journalpost: $path")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                val response = journalpostOidcRestTemplate.exchange(
                        path,
                        HttpMethod.POST,
                        HttpEntity(request.toString(), headers),
                        String::class.java)
                mapper.readValue(response.body, OpprettJournalPostResponse::class.java)
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex")
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message}")
            }
        }
    }

    /**
     *  Oppdaterer journaposten. Kanal og ekspedertstatus settes med {@code OppdaterDistribusjonsinfoRequest}
     *  @param journalpostId: Journalposten sin ID
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) {
        val path = "/journalpost/$journalpostId/oppdaterDistribusjonsinfo"

        return oppdaterDistribusjonsinfo.measure {
            try {
                logger.info("Oppdaterer distribusjonsinfo for journalpost: $journalpostId")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                journalpostOidcRestTemplate.exchange(
                        path,
                        HttpMethod.PATCH,
                        HttpEntity(OppdaterDistribusjonsinfoRequest().toString(), headers),
                        String::class.java)

            } catch (ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: $ex")
                throw RuntimeException("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: ${ex.message}")
            }
        }
    }
}
