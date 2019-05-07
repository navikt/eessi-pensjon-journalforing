package no.nav.eessi.pensjon.journalforing.services.oppgave

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

private val logger = LoggerFactory.getLogger(OppgaveService::class.java)

@Service
class OppgaveService(val oppgaveOidcRestTemplate: RestTemplate) {

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgave(oppgave: Oppgave) {

        val requestBody = jacksonObjectMapper()
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .writeValueAsString(oppgave)
        val httpEntity = HttpEntity(requestBody)

            val responseEntity = oppgaveOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java)
            validateResponseEntity(responseEntity)
            jacksonObjectMapper().readTree(responseEntity.body!!)["id"].asInt()
    }

    private fun validateResponseEntity(responseEntity: ResponseEntity<String>) {
        if (responseEntity.statusCode.isError) {
            logger.error("Received ${responseEntity.statusCode} from Oppgave")
            logger.error(responseEntity.body.toString())
            throw RuntimeException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from Oppgave")
        }

        if (!responseEntity.hasBody())
            throw RuntimeException("Response from Oppgave is empty")
    }
}
