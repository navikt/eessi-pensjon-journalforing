package no.nav.eessi.pensjon.journalforing.services.oppgave

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger(OppgaveService::class.java)

@Service
class OppgaveService(val oppgaveOidcRestTemplate: RestTemplate) {

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgave(sedHendelse: SedHendelse, journalPostResponse: JournalPostResponse, aktoerId: String?) {


        val requestBody = jacksonObjectMapper()
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .writeValueAsString(populerOppgaveFelter(sedHendelse, journalPostResponse, aktoerId))
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

    fun populerOppgaveFelter(sedHendelse: SedHendelse,
                             journalPostResponse: JournalPostResponse,
                             aktoerId: String?) : Oppgave {
        val oppgave = Oppgave()
        oppgave.oppgavetype = Oppgave.OppgaveType.JOURNALFORING.name

        if(sedHendelse.bucType.equals("P_BUC_03")) {
            oppgave.tema = Oppgave.Tema.UFORETRYGD.name
            oppgave.behandlingstema = Oppgave.Behandlingstema.UFORE_UTLAND.name
            oppgave.temagruppe = Oppgave.Temagruppe.UFORETRYDG.name

        } else {
            oppgave.tema = Oppgave.Tema.PENSJON.name
            oppgave.behandlingstema = Oppgave.Behandlingstema.UTLAND.name
            oppgave.temagruppe = Oppgave.Temagruppe.PENSJON.name

        }
        oppgave.prioritet = Oppgave.Prioritet.NORM.name
        oppgave.aktoerId = aktoerId
        oppgave.aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        oppgave.behandlingstype = Oppgave.Behandlingstype.UTLAND.name
        oppgave.journalpostId = "1234"
        oppgave.opprettetAvEnhetsnr = "9999"

        return oppgave
    }
}
