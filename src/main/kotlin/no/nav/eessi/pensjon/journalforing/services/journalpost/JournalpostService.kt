package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelse
import no.nav.eessi.pensjon.journalforing.utils.counter
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

@Service
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }
    private val mapper = jacksonObjectMapper()

    private final val opprettJournalpostNavn = "eessipensjon_journalforing.opprettjournalpost"
    private val opprettJournalpostVellykkede = counter(opprettJournalpostNavn, "vellykkede")
    private val opprettJournalpostFeilede = counter(opprettJournalpostNavn, "feilede")

    private final val genererJournalpostModelNavn = "eessipensjon_journalforing.genererjournalpostmodel"
    private val genererJournalpostModelVellykkede = counter(genererJournalpostModelNavn, "vellykkede")
    private val genererJournalpostModelFeilede = counter(genererJournalpostModelNavn, "feilede")

    fun byggJournalPostRequest(sedHendelse: SedHendelse, sedDokumenter: SedDokumenterResponse): JournalpostRequest{

        try {
            val avsenderMottaker = when {
                sedHendelse.mottakerNavn == null -> null
                else -> AvsenderMottaker(
                        id = sedHendelse.mottakerId,
                        navn = sedHendelse.mottakerNavn
                )
            }

            val behandlingstema = BUCTYPE.valueOf(sedHendelse.bucType!!).BEHANDLINGSTEMA

            val bruker = when {
                sedHendelse.navBruker != null -> Bruker(id = sedHendelse.navBruker)
                else -> null
            }

            val dokumenter = listOf(Dokument(
                        brevkode = sedHendelse.sedId,
                        dokumentvarianter = populerDokumentVarianter(sedDokumenter)))

            val tema = BUCTYPE.valueOf(sedHendelse.bucType).TEMA

            val tittel = when {
                sedHendelse.sedType != null -> "Utgående ${sedHendelse.sedType}"
                else -> throw RuntimeException("sedType er null")
            }

            return JournalpostRequest(
                avsenderMottaker = avsenderMottaker,
                behandlingstema = behandlingstema,
                bruker = bruker,
                dokumenter = dokumenter,
                tema = tema,
                tittel = tittel
            )
        } catch (ex: Exception){
            genererJournalpostModelFeilede.increment()
            logger.error("noe gikk galt under konstruksjon av JournalpostModel, $ex")
            throw RuntimeException("Feil ved konstruksjon av JournalpostModel, $ex")
        }
    }

    fun populerDokumentVarianter(sedDokumenter: SedDokumenterResponse) : List<Dokumentvarianter> {
        val dokumenter = mutableListOf<Dokumentvarianter>()
        dokumenter.add(Dokumentvarianter(fysiskDokument = sedDokumenter.sed.innhold, filtype = sedDokumenter.sed.mimeType.decode()))
        sedDokumenter.vedlegg?.forEach{ vedlegg ->
            dokumenter.add(Dokumentvarianter(fysiskDokument = vedlegg.innhold, filtype = vedlegg.mimeType.decode()))
        }
        return dokumenter
    }

    fun opprettJournalpost(sedHendelse: SedHendelse,
                           sedDokumenter: SedDokumenterResponse,
                           forsokFerdigstill: Boolean) :JournalPostResponse {

        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        try {
            logger.info("Kaller Journalpost for å generere en journalpost")

            val requestBody = byggJournalPostRequest(sedHendelse = sedHendelse, sedDokumenter = sedDokumenter).toString()
            genererJournalpostModelVellykkede.increment()


            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = journalpostOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    HttpEntity(requestBody, headers),
                    String::class.java
            )

            if(!response.statusCode.isError) {
                opprettJournalpostVellykkede.increment()
                logger.debug(response.body.toString())
                return mapper.readValue(response.body, JournalPostResponse::class.java)

            } else {
                throw RuntimeException("Noe gikk galt under opprettelse av journalpostoppgave")
            }
        } catch(ex: Exception) {
            opprettJournalpostFeilede.increment()
            logger.error("noe gikk galt under opprettelse av journalpostoppgave, $ex")
            throw RuntimeException("Feil ved opprettelse av journalpostoppgave, $ex")
        }
    }

}