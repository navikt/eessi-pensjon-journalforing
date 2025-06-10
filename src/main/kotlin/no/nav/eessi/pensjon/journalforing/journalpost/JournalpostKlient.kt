package no.nav.eessi.pensjon.journalforing.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.OppdaterDistribusjonsinfoRequest
import no.nav.eessi.pensjon.journalforing.OpprettJournalPostResponse
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJson
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
     * Regler i Joark for å få forsoekFerdigstill=true ligger her: https://confluence.adeo.no/display/BOA/opprettJournalpost
     *
     * @return {@link OpprettJournalPostResponse}
     *         Respons fra Joark. Inneholder journalposten sin ID, status, melding, og en boolean-verdi
     *         som indikerer om posten ble ferdigstilt.
     */
    fun opprettJournalpost(request: OpprettJournalpostRequest, forsokFerdigstill: Boolean, saksbehandlerIdent: String?): OpprettJournalPostResponse? {
        logger.info("Forsøker å ferdigstille journalpost: $forsokFerdigstill")

        return opprettjournalpost.measure {
            return@measure try {
                logger.info("Kaller Joark for å generere en journalpost: /journalpost?forsoekFerdigstill=$forsokFerdigstill")

                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                if(!saksbehandlerIdent.isNullOrBlank()) {
                    headers["Nav-User-Id"] = saksbehandlerIdent
                }

                if (!request.sak?.fagsakid.erGyldigPesysNummerEllerGjenny()) {
                    throw IllegalArgumentException("Ugyldig Pesys-nummer: ${request.sak?.fagsakid}")
                }

                secureLog.info("Journalpostrequesten: ${request.copy(dokumenter = "***").toJson()}, header: $headers")


                val response = journalpostOidcRestTemplate.exchange(
                    "/journalpost?forsoekFerdigstill=$forsokFerdigstill",
                        HttpMethod.POST,
                        HttpEntity(request.toJson(), headers),
                        String::class.java)
                logger.info("""Journalpost er opprettet med status: ${response.statusCode}
                    | ${response.body}
                """.trimMargin())
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

    private fun OpprettJournalpostRequest.maskerteVerdier(): OpprettJournalpostRequest {
        if (dokumenter.isNotEmpty()) {
            return this.copy(dokumenter = dokumenter.take(20) + "**********")
        }
        return this
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

    fun oppdaterJournalpostMedAvbrutt(journalpostId: String) {

        return avbruttStatusInfo.measure {
            try {
                logger.info("Setter status avbryt for journalpost: $journalpostId")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                journalpostOidcRestTemplate.exchange(
                    "/journalpost/$journalpostId/feilregistrer/settStatusAvbryt",
                    HttpMethod.PATCH,
                    HttpEntity("", headers),
                    String::class.java
                )

            } catch (ex: Exception) {
                handleException("forsøk på å sette status til avbrutt på journalpostId: $journalpostId ex: ", ex).also {
                    throw RuntimeException(it)
                }
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

    fun String?.erGyldigPesysNummerEllerGjenny(): Boolean {
        if (this.isNullOrEmpty()) return false
        return (this.length in listOf(5, 8) && this.first() in listOf('1', '2') && this.all { it.isDigit() })
    }
}
