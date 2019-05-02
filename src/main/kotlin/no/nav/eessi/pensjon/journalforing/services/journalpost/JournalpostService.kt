package no.nav.eessi.pensjon.journalforing.services.journalpost

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import kotlin.RuntimeException

@Service
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }

    fun opprettJournalpost(requestBody: JournalpostModel):String? {
        val path = "/journalpost?forsoekFerdigstill=false"

        val builder = UriComponentsBuilder.fromUriString(path).build()
        val httpEntity = HttpEntity("")

        try {
            logger.info("Kaller Journalpost for å generere en journalpost")
            val response = journalpostOidcRestTemplate.exchange(builder.toUriString(),
                HttpMethod.POST,
                    httpEntity,
                    String::class.java)
            if(!response.statusCode.isError) {
                logger.debug(response.body.toString())
                return response.body
            } else {
                throw RuntimeException("Noe gikk galt under opprettelse av journalpostoppgave")
            }
        } catch(ex: Exception) {
            logger.error("noe gikk galt under opprettelse av journalpostoppgave")
            throw RuntimeException("Feil ved opprettelse av journalpostoppgave")
        }
    }

}