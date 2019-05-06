package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.SedHendelse
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

    //TODO avklare om dette er riktige behandlinstema
    enum class BUC_TYPE (val BEHANDLINGSTEMA: String){
        P_BUC_01("ab0254"),
        P_BUC_02("ab0011"),
        P_BUC_03("ab0194")
    }

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }

    fun opprettJournalpost(sedHendelse: SedHendelse, pdfBody: String, forsokFerdigstill: Boolean):String? {
        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()
        val mapper = jacksonObjectMapper()

        try {
            logger.info("Kaller Journalpost for å generere en journalpost")

            val avsenderMottaker= when {
                sedHendelse.mottakerNavn == null -> null
                else -> AvsenderMottaker(
                            id= sedHendelse.mottakerId,
                            navn = sedHendelse.mottakerNavn
                )
            }

            val behandlingstema = when {
                sedHendelse.bucType == BUC_TYPE.P_BUC_01.name -> BUC_TYPE.P_BUC_01.BEHANDLINGSTEMA
                sedHendelse.bucType == BUC_TYPE.P_BUC_02.name -> BUC_TYPE.P_BUC_02.BEHANDLINGSTEMA
                sedHendelse.bucType == BUC_TYPE.P_BUC_03.name -> BUC_TYPE.P_BUC_03.BEHANDLINGSTEMA
                else -> null
            }

            val bruker = when{
                sedHendelse.navBruker != null -> Bruker(id= sedHendelse.navBruker)
                else -> null
            }

            val dokumenter = listOf(Dokument(
                    brevkode= sedHendelse.sedId,
                    dokumentvarianter = listOf(Dokumentvarianter(
                            fysiskDokument = pdfBody
                    ))))

            val tittel = "Utgående ${sedHendelse.sedType}"

            val journalPostRequestBody = JournalpostModel(
                    avsenderMottaker = avsenderMottaker,
                    behandlingstema = behandlingstema,
                    bruker = bruker,
                    dokumenter = dokumenter,
                    tittel = tittel
            )

            val response = journalpostOidcRestTemplate.exchange(builder.toUriString(),
                HttpMethod.POST,
                    HttpEntity( mapper.writeValueAsString(journalPostRequestBody) ),
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