package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelse
import no.nav.eessi.pensjon.journalforing.utils.mapAnyToJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import org.springframework.http.*


@RunWith(MockitoJUnitRunner::class)
class JournalpostServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate
    lateinit var journalpostService: JournalpostService
    lateinit var sedHendelse: SedHendelse

    val responseBody = "{\"journalpostId\": \"string\", \"journalstatus\": \"MIDLERTIDIG\", \"melding\": \"string\"}"
    val mapper = jacksonObjectMapper()

    @Before
    fun setup() {
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_01.json")))
        sedHendelse = mapper.readValue(sedSendtJson, SedHendelse::class.java)
        journalpostService = JournalpostService(mockrestTemplate)
    }

    @Test
    fun `Gitt gyldig SedHendelse så bygg Gyldig JournalpostModel`() {
        val objectToTest = journalpostService.byggJournalPostRequest(sedHendelse= sedHendelse, pdfBody = "MockPdfBody")

        assertEquals(objectToTest.avsenderMottaker?.id, sedHendelse.mottakerId, "Ugyldig mottagerid")
        assertEquals(objectToTest.avsenderMottaker?.navn, sedHendelse.mottakerNavn, "Ugyldig mottagernavn")
        assertEquals(objectToTest.behandlingstema, BUCTYPE.valueOf(sedHendelse.bucType!!).BEHANDLINGSTEMA, "Ugyldig behandlingstema")
        assertEquals(objectToTest.bruker?.id, sedHendelse.navBruker, "Ugyldig bruker id")
        assertEquals(objectToTest.dokumenter.first().brevkode, sedHendelse.sedId, "Ugyldig brevkode")
        assertEquals(objectToTest.dokumenter.first().dokumentvarianter.first().fysiskDokument, "MockPdfBody", "Ugyldig fysisk dokument")
        assertEquals(objectToTest.tema, BUCTYPE.valueOf(sedHendelse.bucType!!).TEMA, "Ugyldig tema")
        assertEquals(objectToTest.tittel,"Utgående ${sedHendelse.sedType}", "Ugyldig tittel")
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        doReturn(
                ResponseEntity(responseBody, HttpStatus.OK))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost?forsoekFerdigstill=false"),
                        eq(HttpMethod.POST),
                        eq(HttpEntity(
                                mapAnyToJson(
                                        journalpostService.byggJournalPostRequest(sedHendelse = sedHendelse, pdfBody = "MockPdfBody"),
                                        true),
                                headers)),
                        eq(String::class.java))

        journalpostService.opprettJournalpost(sedHendelse = sedHendelse, pdfBody = "MockPdfBody", forsokFerdigstill = false)
    }

    @Test( expected = RuntimeException::class)
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        doReturn(
                ResponseEntity("", HttpStatus.INTERNAL_SERVER_ERROR))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost?forsoekFerdigstill=false"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        journalpostService.opprettJournalpost(sedHendelse= sedHendelse, pdfBody = "String", forsokFerdigstill = false)
    }
}