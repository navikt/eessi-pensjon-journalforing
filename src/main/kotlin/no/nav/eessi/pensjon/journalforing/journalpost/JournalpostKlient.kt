package no.nav.eessi.pensjon.journalforing.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.JournalpostModel
import no.nav.eessi.pensjon.journalforing.OppdaterDistribusjonsinfoRequest
import no.nav.eessi.pensjon.journalforing.OpprettJournalPostResponse
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.saf.OppdaterJournalpost
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class JournalpostKlient(
    private val journalpostOidcRestTemplate: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostKlient::class.java) }
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private val mapper = jacksonObjectMapper()

    private lateinit var opprettjournalpost: MetricsHelper.Metric
    private lateinit var oppdaterDistribusjonsinfo: MetricsHelper.Metric
    private lateinit var avbruttStatusInfo: MetricsHelper.Metric
    private lateinit var ferdigstillJournal: MetricsHelper.Metric


    init {
        avbruttStatusInfo = metricsHelper.init("avbruttStatusInfo")
        opprettjournalpost = metricsHelper.init("opprettjournalpost")
        oppdaterDistribusjonsinfo = metricsHelper.init("oppdaterDistribusjonsinfo")
        ferdigstillJournal = metricsHelper.init("ferdigstillJournal")

    }

    /**
     * Sender et POST request til Joark for opprettelse av JournalPost.
     *
     * @param request: Request-objektet som skal sendes til joark.
     * @param forsokFerdigstill: Hvis true vil Joark forsøke å ferdigstille journalposten.
     *
     * @return {@link OpprettJournalPostResponse}
     *         Respons fra Joark. Inneholder journalposten sin ID, status, melding, og en boolean-verdi
     *         som indikerer om posten ble ferdigstilt.
     */
    fun opprettJournalpost(request: OpprettJournalpostRequest, forsokFerdigstill: Boolean, saksbehandlerIdent: String?): OpprettJournalPostResponse? {
        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        if (forsokFerdigstill == true) {
            logger.info("Forsøker å ferdigstille journalpost")
        }

        return opprettjournalpost.measure {
            return@measure try {
                logger.info("Kaller Joark for å generere en journalpost: $path")

                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                if(!saksbehandlerIdent.isNullOrBlank()) {
                    headers["Nav-User-Id"] = saksbehandlerIdent
                }

                secureLog.info("Journalpostrequesten: $request, /n $headers")

                val response = journalpostOidcRestTemplate.exchange(
                        path,
                        HttpMethod.POST,
                        HttpEntity(request.toString(), headers),
                        String::class.java)
                mapper.readValue(response.body, OpprettJournalPostResponse::class.java)
            } catch (ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: ", ex)
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: ", ex)
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message}")
            }
        }
    }

    /**
     *  Oppdaterer journaposten. Kanal og ekspedertstatus settes med {@code OppdaterDistribusjonsinfoRequest}.
     *  Dette låser og ferdigstiller journalposten!
     *
     *  @param journalpostId: Journalposten som skal oppdateres.
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

            } catch (ex: Exception) {
                handleException("oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: ", ex)
            }
        }
    }

    fun ferdigstillJournalpost(journalpostId: String, journalfoerendeEnhet: String): JournalpostModel.FerdigJournalpost {
        val path = "/journalpost/$journalpostId/ferdigstill"
        ferdigstillJournal.measure {
            try {
                logger.info("Forsøker å ferdigstille journalpost: $journalpostId")
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }

                val requestBody = """{ "journalfoerendeEnhet": "$journalfoerendeEnhet" } """.trimIndent()

                val response = journalpostOidcRestTemplate.exchange(
                    path,
                    HttpMethod.PATCH,
                    HttpEntity(requestBody, headers),
                    String::class.java
                )
                if (response.statusCode == HttpStatus.OK) {
                    return@measure JournalpostModel.Ferdigstilt("Journalpost: $journalpostId er ferdigstilt")
                } else {
                    return@measure JournalpostModel.IngenFerdigstilling("""
                        Journalpost: $journalpostId er ikke ferdigstilt
                        Feilmelding: ${response.body}""".trimIndent()
                    )
                }
            } catch (ex: Exception) {
                val errorMessage = "ferdigstilling av journalpost: $journalpostId"
                handleException(errorMessage, ex).also {
                    return@measure JournalpostModel.IngenFerdigstilling(it)
                }
            }
        }
        return JournalpostModel.IngenFerdigstilling("Ingen gyldig verdi")
    }

    fun oppdaterJournalpostMedAvbrutt(journalpostId: String) {
        val path = "/journalpost/$journalpostId/feilregistrer/settStatusAvbryt"

        return avbruttStatusInfo.measure {
            try {
                logger.info("Setter status avbryt for journalpost: $journalpostId")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                journalpostOidcRestTemplate.exchange(
                    path,
                    HttpMethod.PATCH,
                    HttpEntity("",headers),
                    String::class.java)

            } catch (ex: Exception) {
                handleException("forsøk på å sette status til avbrutt på journalpostId: $journalpostId ex: ", ex).also {
                    throw RuntimeException(it)
                }
            }
        }
    }

    fun oppdaterJournalpostMedBruker(oppdaterbarJournalpost: OppdaterJournalpost) {
        val path = "/journalpost/${oppdaterbarJournalpost.journalpostId}"

        try {
            logger.info("Oppdaterer journalpost med journalpostId: ${oppdaterbarJournalpost.journalpostId}")
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            journalpostOidcRestTemplate.exchange(
                path,
                HttpMethod.PUT,
                HttpEntity(oppdaterbarJournalpost.toJson(), headers),
                String::class.java).also {
                    logger.info("JournalpostId ${oppdaterbarJournalpost.journalpostId} har blitt oppdatert med kjent bruker" )
                }

        } catch (ex: Exception) {
            handleException("oppdatering journalpost med journalpostId: ${oppdaterbarJournalpost.journalpostId} ex: ", ex).also {
                throw RuntimeException(it)
            }
        }
    }
    private fun handleException(context: String, ex: Exception) : String {
        return if (ex is HttpStatusCodeException) {
            "Feil under $context: ${ex.message}, body: ${ex.responseBodyAsString}"
        } else {
            "Feil under $context: ${ex.message}"
        }.also {
            logger.error(it, ex)
        }
    }
}
