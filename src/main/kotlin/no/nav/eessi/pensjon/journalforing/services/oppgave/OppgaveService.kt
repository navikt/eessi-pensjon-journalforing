package no.nav.eessi.pensjon.journalforing.services.oppgave

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
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
class OppgaveService(val oppgaveOidcRestTemplate: RestTemplate, val oppgaveRoutingService: OppgaveRoutingService) {

    private final val opprettOppgaveNavn = "eessipensjon_journalforing.opprettoppgave"
    private val opprettOppgaveVellykkede = counter(opprettOppgaveNavn, "vellykkede")
    private val opprettOppgaveFeilede = counter(opprettOppgaveNavn, "feilede")

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgave(sedHendelse: SedHendelseModel,
                       journalPostResponse: JournalPostResponse?,
                       aktoerId: String?,
                       landkode: String?,
                       fodselsDato: String,
                       ytelseType: OppgaveRoutingModel.YtelseType?,
                       oppgaveType: Oppgave.OppgaveType) {

        val requestBody = mapAnyToJson(populerOppgaveFelter(sedHendelse,
                journalPostResponse,
                aktoerId,
                landkode,
                fodselsDato,
                ytelseType,
                oppgaveType), true)
        val httpEntity = HttpEntity(requestBody)

        try {
            logger.info("Oppretter $oppgaveType oppgave")
            val responseEntity = oppgaveOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java)
            validateResponseEntity(responseEntity)
            opprettOppgaveVellykkede.increment()
            val tildeltEnhetsnr = jacksonObjectMapper().readTree(responseEntity.body!!)["tildeltEnhetsnr"].asInt()
            logger.info("Opprettet journalforingsoppgave med tildeltEnhetsnr:  $tildeltEnhetsnr")
            jacksonObjectMapper().readTree(responseEntity.body!!)["id"].asInt()
        } catch (ex: Exception) {
            logger.error("En feil oppstod under opprettelse av oppgave: $ex")
            opprettOppgaveFeilede.increment()
            throw RuntimeException("En feil oppstod under opprettelse av oppgave: $ex")
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

    fun populerOppgaveFelter(sedHendelse: SedHendelseModel,
                             journalPostResponse: JournalPostResponse?,
                             aktoerId: String?,
                             landkode: String?,
                             fodselsDato: String,
                             ytelseType: OppgaveRoutingModel.YtelseType?,
                             oppgaveType: Oppgave.OppgaveType) : Oppgave {
        val oppgave = Oppgave()
        oppgave.oppgavetype = oppgaveType.toString()
        oppgave.tema = Oppgave.Tema.PENSJON.toString()
        oppgave.prioritet = Oppgave.Prioritet.NORM.toString()
        if(aktoerId != null) {
            oppgave.aktoerId = aktoerId
        }
        oppgave.aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        if(journalPostResponse != null) {
            oppgave.journalpostId = journalPostResponse.journalpostId
        }
        oppgave.opprettetAvEnhetsnr = "9999"
        oppgave.tildeltEnhetsnr = oppgaveRoutingService.route(sedHendelse, landkode, fodselsDato, ytelseType).enhetsNr
        oppgave.fristFerdigstillelse = LocalDate.now().plusDays(1).toString()
        if(oppgaveType == Oppgave.OppgaveType.JOURNALFORING) {
            oppgave.beskrivelse = sedHendelse.sedType.toString()
        } else if (oppgaveType == Oppgave.OppgaveType.BEHANDLE_SED) {
            oppgave.beskrivelse = "Bytt denne teksten med noe annet............."
        } else {
            throw RuntimeException("Ukjent eller manglende oppgavetype under opprettelse av oppgave")
        }

        return oppgave
    }
}
