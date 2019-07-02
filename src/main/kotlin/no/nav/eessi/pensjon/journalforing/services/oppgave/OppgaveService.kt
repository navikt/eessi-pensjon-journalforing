package no.nav.eessi.pensjon.journalforing.services.oppgave

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.json.mapAnyToJson
import no.nav.eessi.pensjon.journalforing.models.sed.SedHendelseModel
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class OppgaveService(private val oppgaveOidcRestTemplate: RestTemplate, private val oppgaveRoutingService: OppgaveRoutingService) {

    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)

    private final val opprettOppgaveNavn = "eessipensjon_journalforing.opprettoppgave"
    private val opprettOppgaveVellykkede = counter(opprettOppgaveNavn, "vellykkede")
    private val opprettOppgaveFeilede = counter(opprettOppgaveNavn, "feilede")

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgave(opprettOppgaveModel: OpprettOppgaveModel) {

        val requestBody = mapAnyToJson(populerOppgaveFelter(opprettOppgaveModel), true)
        val httpEntity = HttpEntity(requestBody)

        try {
            logger.info("Oppretter ${opprettOppgaveModel.oppgaveType} oppgave")
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

    private fun populerOppgaveFelter(opprettOppgaveModel: OpprettOppgaveModel) : Oppgave {
        val oppgave = Oppgave()
        oppgave.oppgavetype = opprettOppgaveModel.oppgaveType.oppgaveType.toString()
        oppgave.tema = Oppgave.Tema.PENSJON.toString()
        oppgave.prioritet = Oppgave.Prioritet.NORM.toString()
        if(opprettOppgaveModel.aktoerId != null) {
            oppgave.aktoerId = opprettOppgaveModel.aktoerId
        }
        oppgave.aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        if(opprettOppgaveModel.journalPostResponse != null) {
            oppgave.journalpostId = opprettOppgaveModel.journalPostResponse!!.journalpostId
        }
        oppgave.opprettetAvEnhetsnr = "9999"
        oppgave.tildeltEnhetsnr = oppgaveRoutingService.route(opprettOppgaveModel.sedHendelse,
                opprettOppgaveModel.landkode,
                opprettOppgaveModel.fodselsDato,
                opprettOppgaveModel.ytelseType).enhetsNr
        oppgave.fristFerdigstillelse = LocalDate.now().plusDays(1).toString()
        when {
            opprettOppgaveModel.oppgaveType == OpprettOppgaveModel.OppgaveType.JOURNALFORING -> oppgave.beskrivelse = opprettOppgaveModel.sedHendelse.sedType.toString()
            opprettOppgaveModel.oppgaveType == OpprettOppgaveModel.OppgaveType.BEHANDLE_SED -> oppgave.beskrivelse = "Mottatt vedlegg: ${opprettOppgaveModel.filnavn.toString()} tilhørende RINA sakId: ${opprettOppgaveModel.rinaSakId} er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff )"
            else -> throw RuntimeException("Ukjent eller manglende oppgavetype under opprettelse av oppgave")
        }

        return oppgave
    }
}

data class OpprettOppgaveModel(
        val sedHendelse: SedHendelseModel,
        val journalPostResponse: JournalPostResponse?,
        val aktoerId: String?,
        val landkode: String?,
        val fodselsDato: String,
        val ytelseType: OppgaveRoutingModel.YtelseType?,
        val oppgaveType: OppgaveType,
        val rinaSakId: String?,
        val filnavn: List<String>?) {

    enum class OppgaveType(val oppgaveType: Oppgave.OppgaveType) {
        GENERELL(Oppgave.OppgaveType.GENERELL),
        JOURNALFORING(Oppgave.OppgaveType.JOURNALFORING),
        BEHANDLE_SED(Oppgave.OppgaveType.JOURNALFORING)
    }

}

private data class Oppgave(
        var id: Long? = null,
        var tildeltEnhetsnr: String? = null,
        var endretAvEnhetsnr: String? = null,
        var opprettetAvEnhetsnr: String? = null,
        var journalpostId: String? = null,
        var journalpostkilde: String? = null,
        var behandlesAvApplikasjon: String? = null,
        var saksreferanse: String? = null,
        var bnr: String? = null,
        var samhandlernr: String? = null,
        var aktoerId: String? = null,
        var orgnr: String? = null,
        var tilordnetRessurs: String? = null,
        var beskrivelse: String? = null,
        var temagruppe: String? = null,
        var tema: String? = null,
        var behandlingstema: String? = null,
        var oppgavetype: String? = null,
        var behandlingstype: String? = null,
        var prioritet: String? = null,
        var versjon: String? = null,
        var mappeId: String? = null,
        var fristFerdigstillelse: String? = null,
        var aktivDato: String? = null,
        var opprettetTidspunkt: String? = null,
        var opprettetAv: String? = null,
        var endretAv: String? = null,
        var ferdigstiltTidspunkt: String? = null,
        var endretTidspunkt: String? = null,
        var status: String? = null,
        var metadata: Map<String, String>? = null
) {

    enum class OppgaveType : Code {
        GENERELL {
            override fun toString() = "GEN"
            override fun decode() = "Generell oppgave"
        },
        JOURNALFORING {
            override fun toString() = "JFR"
            override fun decode() = "Journalføringsoppgave"
        },
        BEHANDLE_SED {
            override fun toString() = "BEH_SED"
            override fun decode() = "Behandle SED"
        }
    }

    enum class Tema : Code {
        PENSJON {
            override fun toString() = "PEN"
            override fun decode() = "Pensjon"
        },
        UFORETRYGD {
            override fun toString() = "UFO"
            override fun decode() = "Uføretrygd"
        }
    }

    enum class Behandlingstema : Code {
        UTLAND {
            override fun toString() = "ab0313"
            override fun decode() = "Utland"
        },
        UFORE_UTLAND {
            override fun toString() = "ab0039"
            override fun decode() = "Uføreytelser fra utlandet"
        }
    }

    enum class Temagruppe : Code {
        PENSJON {
            override fun toString() = "PENS"
            override fun decode() = "Pensjon"
        },
        UFORETRYDG {
            override fun toString() = "UFRT"
            override fun decode() = "Uføretrydg"
        }
    }

    enum class Behandlingstype : Code {
        MOTTA_SOKNAD_UTLAND {
            override fun toString() = "ae0110"
            override fun decode() = "Motta søknad utland"
        },
        UTLAND {
            override fun toString() = "ae0106"
            override fun decode() = "Utland"
        }
    }

    enum class Prioritet {
        HOY,
        NORM,
        LAV
    }

    interface Code {
        fun decode(): String
    }
}
