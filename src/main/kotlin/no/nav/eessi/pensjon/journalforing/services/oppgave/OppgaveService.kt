package no.nav.eessi.pensjon.journalforing.services.oppgave

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelse
import no.nav.eessi.pensjon.journalforing.utils.counter
import no.nav.eessi.pensjon.journalforing.utils.mapAnyToJson
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

    private final val opprettOppgaveNavn = "eessipensjon_journalforing.opprettoppgave"
    private val opprettOppgaveVellykkede = counter(opprettOppgaveNavn, "vellykkede")
    private val opprettOppgaveFeilede = counter(opprettOppgaveNavn, "feilede")

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgave(sedHendelse: SedHendelse, journalPostResponse: JournalPostResponse, aktoerId: String?) {

        val requestBody = mapAnyToJson(populerOppgaveFelter(sedHendelse, journalPostResponse, aktoerId), true)
        val httpEntity = HttpEntity(requestBody)

        try {
            logger.info("Oppretter oppgave")
            val responseEntity = oppgaveOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java)
            validateResponseEntity(responseEntity)
            opprettOppgaveVellykkede.increment()
            val tildeltEnhetsnr = jacksonObjectMapper().readTree(responseEntity.body!!)["tildeltEnhetsnr"].asInt()
            logger.info("Opprettet journalforingsoppgave med tildeltEnhetsnr:  $tildeltEnhetsnr")
            jacksonObjectMapper().readTree(responseEntity.body!!)["id"].asInt()
        } catch (ex: Exception) {
            logger.error("En feil oppstod under opprettelse av oppgave; ${ex.message}")
            opprettOppgaveFeilede.increment()
        }
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
        oppgave.oppgavetype = Oppgave.OppgaveType.JOURNALFORING.toString()

        if(sedHendelse.bucType.equals("P_BUC_03")) {
            oppgave.tema = Oppgave.Tema.UFORETRYGD.toString()
//            oppgave.behandlingstema = Oppgave.Behandlingstema.UFORE_UTLAND.toString()
//            oppgave.temagruppe = Oppgave.Temagruppe.UFORETRYDG.toString()

        } else {
            oppgave.tema = Oppgave.Tema.PENSJON.toString()
//            oppgave.behandlingstema = Oppgave.Behandlingstema.UTLAND.toString()
//            oppgave.temagruppe = Oppgave.Temagruppe.PENSJON.toString()

        }
        oppgave.prioritet = Oppgave.Prioritet.NORM.toString()
        oppgave.aktoerId = aktoerId
        oppgave.aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        //oppgave.behandlingstype = Oppgave.Behandlingstype.UTLAND.toString()
        oppgave.journalpostId = journalPostResponse.journalpostId
        oppgave.opprettetAvEnhetsnr = "9999"

        return oppgave
    }
}
