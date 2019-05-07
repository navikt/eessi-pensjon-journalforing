package no.nav.eessi.pensjon.journalforing.services.journalpost

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import kotlin.RuntimeException
import no.nav.eessi.pensjon.journalforing.utils.BUCTYPE
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

@Service
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }

    fun byggJournalPostModel(sedHendelse: SedHendelse, pdfBody: String): JournalpostModel{

        try {
            val avsenderMottaker = when {
                sedHendelse.mottakerNavn == null -> null
                else -> AvsenderMottaker(
                        id = sedHendelse.mottakerId,
                        navn = sedHendelse.mottakerNavn
                )
            }

            val behandlingstema = when {
                sedHendelse.bucType.equals(BUCTYPE.P_BUC_01.name, true) -> BUCTYPE.P_BUC_01.BEHANDLINGSTEMA
                sedHendelse.bucType.equals(BUCTYPE.P_BUC_02.name, true) -> BUCTYPE.P_BUC_02.BEHANDLINGSTEMA
                sedHendelse.bucType.equals(BUCTYPE.P_BUC_03.name, true) -> BUCTYPE.P_BUC_03.BEHANDLINGSTEMA
                else -> throw RuntimeException("Feil med bucType, ${sedHendelse.bucType}")
            }

            val bruker = when {
                sedHendelse.navBruker != null -> Bruker(id = sedHendelse.navBruker)
                else -> null
            }

            val dokumenter = when {
                pdfBody == null -> throw RuntimeException("pdf er null")
                pdfBody == "" -> throw RuntimeException("pdf er tom")
                else -> listOf(Dokument(
                        brevkode = sedHendelse.sedId,
                        dokumentvarianter = listOf(Dokumentvarianter(
                                fysiskDokument = pdfBody))))
            }

            val tema = BUCTYPE.valueOf(sedHendelse.bucType!!).TEMA

            val tittel = when {
                sedHendelse.sedType != null -> "Utgående ${sedHendelse.sedType}"
                else -> throw RuntimeException("pdf er null")
            }

            return JournalpostModel(
                avsenderMottaker = avsenderMottaker,
                behandlingstema = behandlingstema,
                bruker = bruker,
                dokumenter = dokumenter,
                tema = tema,
                tittel = tittel
            )
        } catch (ex: Exception){
            logger.error("noe gikk galt under konstruksjon av JournalpostModel, $ex")
            throw RuntimeException("Feil ved konstruksjon av JournalpostModel, $ex")
        }
    }

    fun opprettJournalpost(sedHendelse: SedHendelse, pdfBody: String, forsokFerdigstill: Boolean):String? {
        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        try {
            logger.info("Kaller Journalpost for å generere en journalpost")

            val requestBody = byggJournalPostModel(sedHendelse= sedHendelse, pdfBody = pdfBody).toString()

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = journalpostOidcRestTemplate.exchange(builder.toUriString(),
                HttpMethod.POST,
                    HttpEntity(requestBody, headers),
                    String::class.java)
            if(!response.statusCode.isError) {
                logger.debug(response.body.toString())
                return response.body
            } else {
                throw RuntimeException("Noe gikk galt under opprettelse av journalpostoppgave")
            }
        } catch(ex: Exception) {
            logger.error("noe gikk galt under opprettelse av journalpostoppgave, $ex")
            throw RuntimeException("Feil ved opprettelse av journalpostoppgave, $ex")
        }
    }

}