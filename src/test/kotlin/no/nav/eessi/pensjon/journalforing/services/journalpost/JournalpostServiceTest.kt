package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.models.sed.SedHendelseModel
import org.mockito.Mockito.doReturn
import no.nav.eessi.pensjon.journalforing.services.documentconverter.DocumentConverterService
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.IdType.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import org.springframework.http.*

@RunWith(MockitoJUnitRunner::class)
class JournalpostServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    @Mock
    private lateinit var documentConverterService: DocumentConverterService

    private lateinit var journalpostService: JournalpostService
    private lateinit var sedHendelse: SedHendelseModel
    private val mapper = jacksonObjectMapper()

    @Before
    fun setup() {
        journalpostService = JournalpostService(mockrestTemplate, documentConverterService)
    }

    @Test
    fun `Gitt gyldig SedHendelse så bygg Gyldig JournalpostModel`() {
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json")))
        sedHendelse = mapper.readValue(sedSendtJson, SedHendelseModel::class.java)

        val journalpostRequest = journalpostService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenterResponse::class.java),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.behandlingstema, BUCTYPE.valueOf(sedHendelse.bucType.toString()).BEHANDLINGSTEMA, "Ugyldig behandlingstema")
        assertEquals(journalpostRequest.journalpostRequest.bruker?.id, sedHendelse.navBruker, "Ugyldig bruker id")
        assertEquals(journalpostRequest.journalpostRequest.dokumenter.first().brevkode, sedHendelse.sedId, "Ugyldig brevkode")
        assertEquals(journalpostRequest.journalpostRequest.dokumenter.first().dokumentvarianter.first().fysiskDokument, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G", "Ugyldig fysisk dokument")
        assertEquals(journalpostRequest.journalpostRequest.tema, BUCTYPE.valueOf(sedHendelse.bucType.toString()).TEMA, "Ugyldig tema")
        assertEquals(journalpostRequest.journalpostRequest.tittel,"Utgående ${sedHendelse.sedType}", "Ugyldig tittel")
    }

    @Test
    fun `Gitt utgående SED med fnr når populerer request så blir avsenderMottaker FNR`() {
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json")))
        sedHendelse = mapper.readValue(sedSendtJson, SedHendelseModel::class.java)

        val journalpostRequest = journalpostService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenterResponse::class.java),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, FNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt utgående SED uten fnr når populerer request så blir avsenderMottaker NAV`() {
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json")))
        sedHendelse = mapper.readValue(sedSendtJson, SedHendelseModel::class.java)

        val journalpostRequest = journalpostService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenterResponse::class.java),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.avsenderId, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, sedHendelse.avsenderNavn, "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, ORGNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt inngående SED med fnr når populerer request så blir avsenderMottaker FNR`() {
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json")))
        sedHendelse = mapper.readValue(sedSendtJson, SedHendelseModel::class.java)

        val journalpostRequest = journalpostService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenterResponse::class.java),
                sedHendelseType = HendelseType.MOTTATT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, FNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt inngående SED uten fnr når populerer request så blir avsenderMottaker utenlandsk ORG`() {
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json")))
        sedHendelse = mapper.readValue(sedSendtJson, SedHendelseModel::class.java)

        val journalpostRequest = journalpostService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenterResponse::class.java),
                sedHendelseType = HendelseType.MOTTATT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.mottakerId, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, sedHendelse.mottakerNavn, "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, UTL_ORG, "Ugyldig idType")
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json")))

        doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        anyString(),
                        any(),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        val journalpotReponse = journalpostService.opprettJournalpost(
                journalpostRequest = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json"))), JournalpostRequest::class.java),
                sedHendelseType = HendelseType.SENDT,
                forsokFerdigstill = false)
        assertEquals(journalpotReponse.journalpostId, "429434378")
        assertEquals(journalpotReponse.journalstatus, "M")
        assertEquals(journalpotReponse.melding, "null")
    }

    @Test( expected = RuntimeException::class)
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        doReturn(
                ResponseEntity(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json"))), HttpStatus.INTERNAL_SERVER_ERROR))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost?forsoekFerdigstill=false"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        journalpostService.opprettJournalpost(
                journalpostRequest = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json"))), JournalpostRequest::class.java),
                sedHendelseType = HendelseType.SENDT,
                forsokFerdigstill = false)
    }
}