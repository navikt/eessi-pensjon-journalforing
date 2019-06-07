package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.documentconverter.DocumentConverterModel
import no.nav.eessi.pensjon.journalforing.services.documentconverter.DocumentConverterService
import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
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
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate,
                         val dokumentConverterService: DocumentConverterService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }
    private val mapper = jacksonObjectMapper()

    private final val opprettJournalpostNavn = "eessipensjon_journalforing.opprettjournalpost"
    private val opprettJournalpostVellykkede = counter(opprettJournalpostNavn, "vellykkede")
    private val opprettJournalpostFeilede = counter(opprettJournalpostNavn, "feilede")

    private final val genererJournalpostModelNavn = "eessipensjon_journalforing.genererjournalpostmodel"
    private val genererJournalpostModelVellykkede = counter(genererJournalpostModelNavn, "vellykkede")
    private val genererJournalpostModelFeilede = counter(genererJournalpostModelNavn, "feilede")

    fun byggJournalPostRequest(sedHendelseModel: SedHendelseModel, sedDokumenter: SedDokumenterResponse): JournalpostRequest{

        try {
            val avsenderMottaker = when {
                sedHendelseModel.mottakerNavn == null -> null
                else -> AvsenderMottaker(
                        id = sedHendelseModel.mottakerId,
                        navn = sedHendelseModel.mottakerNavn
                )
            }

            val behandlingstema = BUCTYPE.valueOf(sedHendelseModel.bucType.toString()).BEHANDLINGSTEMA

            val bruker = when {
                sedHendelseModel.navBruker != null -> Bruker(id = sedHendelseModel.navBruker)
                else -> null
            }

            val dokumenter =  mutableListOf<Dokument>()

            dokumenter.add(Dokument(sedHendelseModel.sedId,
                    "SED",
                    listOf(Dokumentvarianter(fysiskDokument = sedDokumenter.sed.innhold,
                            filtype = sedDokumenter.sed.mimeType.decode(),
                            variantformat = Variantformat.ARKIV)), sedDokumenter.sed.filnavn))

            sedDokumenter.vedlegg?.forEach{ vedlegg ->
                dokumenter.add(Dokument(sedHendelseModel.sedId,
                        "SED",
                        listOf(Dokumentvarianter(fysiskDokument = vedlegg.innhold,
                                filtype = vedlegg.mimeType.decode(),
                                variantformat = Variantformat.ORIGINAL)), vedlegg.filnavn))
            }

            sedDokumenter.vedlegg?.forEach{ vedlegg ->
                dokumenter.add(Dokument(sedHendelseModel.sedId,
                        "SED",
                        listOf(Dokumentvarianter(MimeType.PDF.decode(),
                                dokumentConverterService.konverterFraBildeTilBase64EncodedPDF(DocumentConverterModel(vedlegg.innhold, MimeType.PDF)),
                                Variantformat.ARKIV)), vedlegg.filnavn))
            }

            val tema = BUCTYPE.valueOf(sedHendelseModel.bucType.toString()).TEMA

            val tittel = when {
                sedHendelseModel.sedType != null -> "Utgående ${sedHendelseModel.sedType}"
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

    fun opprettJournalpost(sedHendelseModel: SedHendelseModel,
                           sedDokumenter: SedDokumenterResponse,
                           forsokFerdigstill: Boolean) :JournalPostResponse {

        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        try {
            logger.info("Kaller Journalpost for å generere en journalpost")

            val requestBody = byggJournalPostRequest(sedHendelseModel = sedHendelseModel, sedDokumenter = sedDokumenter).toString()
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