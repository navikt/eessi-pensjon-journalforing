package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.metrics.counter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import kotlin.RuntimeException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import no.nav.eessi.pensjon.journalforing.models.HendelseType

@Service
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }
    private val mapper = jacksonObjectMapper()

    private final val opprettJournalpostNavn = "eessipensjon_journalforing.opprettjournalpost"
    private val opprettJournalpostVellykkede = counter(opprettJournalpostNavn, "vellykkede")
    private val opprettJournalpostFeilede = counter(opprettJournalpostNavn, "feilede")

    fun opprettJournalpost(journalpostRequest: JournalpostRequest,
                           sedHendelseType: HendelseType,
                           forsokFerdigstill: Boolean) :JournalPostResponse {

        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        try {
            logger.info("Kaller Joark for å generere en journalpost")
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = journalpostOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    HttpEntity(journalpostRequest.toString(), headers),
                    String::class.java
            )

            if(!response.statusCode.isError) {
                opprettJournalpostVellykkede.increment()
                logger.debug(response.body.toString())
                return mapper.readValue(response.body, JournalPostResponse::class.java)
            } else {
                throw RuntimeException("Noe gikk galt under opprettelse av journalpost")
            }
        } catch(ex: Exception) {
            opprettJournalpostFeilede.increment()
            logger.error("noe gikk galt under opprettelse av journalpost, $ex")
            throw RuntimeException("Feil ved opprettelse av journalpost, $ex")
        }
    }
}