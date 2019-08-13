package no.nav.eessi.pensjon.services.oppgave

import no.nav.eessi.pensjon.metrics.counter
import no.nav.eessi.pensjon.json.mapAnyToJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class OppgaveService(private val oppgaveOidcRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)

    private final val opprettOppgaveNavn = "eessipensjon_journalforing.opprettoppgave"
    private val opprettOppgaveVellykkede = counter(opprettOppgaveNavn, "vellykkede")
    private val opprettOppgaveFeilede = counter(opprettOppgaveNavn, "feilede")

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgave(
            sedType: String,
            journalpostId: String?,
            tildeltEnhetsnr: String,
            aktoerId: String?,
            oppgaveType: String,
            rinaSakId: String?,
            filnavn: String?)
    {
        try {

            val oppgaveTypeMap = mapOf(
                    "GENERELL" to Oppgave.OppgaveType.GENERELL,
                    "JOURNALFORING" to Oppgave.OppgaveType.JOURNALFORING,
                    "BEHANDLE_SED" to Oppgave.OppgaveType.BEHANDLE_SED
            )

            val requestBody = mapAnyToJson(
                    Oppgave(
                        oppgavetype = oppgaveTypeMap[oppgaveType].toString(),
                        tema = Oppgave.Tema.PENSJON.toString(),
                        prioritet = Oppgave.Prioritet.NORM.toString(),
                        aktoerId = aktoerId,
                        aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                        journalpostId = journalpostId,
                        opprettetAvEnhetsnr = "9999",
                        tildeltEnhetsnr = tildeltEnhetsnr,
                        fristFerdigstillelse = LocalDate.now().plusDays(1).toString(),
                        beskrivelse = when (oppgaveTypeMap[oppgaveType]) {
                            Oppgave.OppgaveType.JOURNALFORING -> sedType
                            Oppgave.OppgaveType.BEHANDLE_SED -> "Mottatt vedlegg: $filnavn tilhørende RINA sakId: ${rinaSakId} er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff )"
                            else -> throw RuntimeException("Ukjent eller manglende oppgavetype under opprettelse av oppgave")
                    }), true)

            val httpEntity = HttpEntity(requestBody)


            logger.info("Oppretter ${oppgaveType} oppgave")

            val responseEntity = oppgaveOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java)
            validateResponseEntity(responseEntity)

            opprettOppgaveVellykkede.increment()
            logger.info("Opprettet journalforingsoppgave med tildeltEnhetsnr:  $tildeltEnhetsnr")
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
}

private data class Oppgave(
        val id: Long? = null,
        val tildeltEnhetsnr: String? = null,
        val endretAvEnhetsnr: String? = null,
        val opprettetAvEnhetsnr: String? = null,
        val journalpostId: String? = null,
        val journalpostkilde: String? = null,
        val behandlesAvApplikasjon: String? = null,
        val saksreferanse: String? = null,
        val bnr: String? = null,
        val samhandlernr: String? = null,
        val aktoerId: String? = null,
        val orgnr: String? = null,
        val tilordnetRessurs: String? = null,
        val beskrivelse: String? = null,
        val temagruppe: String? = null,
        val tema: String? = null,
        val behandlingstema: String? = null,
        val oppgavetype: String? = null,
        val behandlingstype: String? = null,
        val prioritet: String? = null,
        val versjon: String? = null,
        val mappeId: String? = null,
        val fristFerdigstillelse: String? = null,
        val aktivDato: String? = null,
        val opprettetTidspunkt: String? = null,
        val opprettetAv: String? = null,
        val endretAv: String? = null,
        val ferdigstiltTidspunkt: String? = null,
        val endretTidspunkt: String? = null,
        val status: String? = null,
        val metadata: Map<String, String>? = null
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
