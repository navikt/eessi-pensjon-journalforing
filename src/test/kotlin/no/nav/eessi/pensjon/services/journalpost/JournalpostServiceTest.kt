package no.nav.eessi.pensjon.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import no.nav.eessi.pensjon.json.toJson
import org.mockito.Mockito.doReturn
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.springframework.http.*
import org.springframework.web.client.HttpServerErrorException

@ExtendWith(MockitoExtension::class)
class JournalpostServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    private lateinit var journalpostService: JournalpostService

    @BeforeEach
    fun setup() {
        journalpostService = JournalpostService(mockrestTemplate)
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {

        val journalpostCaptor = argumentCaptor<HttpEntity<Any>>()

        val mapper = jacksonObjectMapper()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json")))
        val requestBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostRequest.json")))


        doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        contains("/journalpost?forsoekFerdigstill=false"),
                        any(),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        val journalpostResponse = journalpostService.opprettJournalpost(
                rinaSakId = "1111",
                fnr= "12345678912",
                personNavn= "navn navnesen",
                bucType= "P_BUC_01",
                sedType= "P2000 - Krav om alderspensjon",
                sedHendelseType= "MOTTATT",
                eksternReferanseId= "string",
                kanal= "NAV_NO",
                journalfoerendeEnhet= "9999",
                arkivsaksnummer= "string",
                dokumenter= """
                    [{
                        "brevkode": "NAV 14-05.09",
                        "dokumentKategori": "SOK",
                        "dokumentvarianter": [
                                {
                                    "filtype": "PDF/A",
                                    "fysiskDokument": "string",
                                    "variantformat": "ARKIV"
                                }
                            ],
                            "tittel": "Søknad om foreldrepenger ved fødsel"
                    }]
                """.trimIndent(),
                forsokFerdigstill= false,
                avsenderLand = "NO",
                avsenderNavn = null
        )

        assertEquals(mapper.readTree(requestBody), mapper.readTree(journalpostCaptor.lastValue.body.toString()))
        assertTrue(journalpostResponse!!.toJson() == "{\n" +
                "  \"journalpostId\" : \"429434378\",\n" +
                "  \"journalstatus\" : \"M\",\n" +
                "  \"melding\" : \"null\",\n" +
                "  \"journalpostferdigstilt\" : false\n" +
                "}")
    }

    @Test
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost?forsoekFerdigstill=false"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        assertThrows<RuntimeException> {
            journalpostService.opprettJournalpost(
                    rinaSakId = "1111",
                    fnr = "12345678912",
                    personNavn = "navn navnesen",
                    bucType = "P_BUC_01",
                    sedType = "P2000 - Krav om alderspensjon",
                    sedHendelseType = "MOTTATT",
                    eksternReferanseId = "string",
                    kanal = "NAV_NO",
                    journalfoerendeEnhet = "9999",
                    arkivsaksnummer = "string",
                    dokumenter = """
                    [{
                        "brevkode": "NAV 14-05.09",
                        "dokumentKategori": "SOK",
                        "dokumentvarianter": [
                                {
                                    "filtype": "PDF/A",
                                    "fysiskDokument": "string",
                                    "variantformat": "ARKIV"
                                }
                            ],
                            "tittel": "Søknad om foreldrepenger ved fødsel"
                    }]
                """.trimIndent(),
                    forsokFerdigstill = false,
                    avsenderLand = "NO",
                    avsenderNavn = null
            )
        }
    }

    @Test
    fun `gittEnUKJournalpostSåByttTilGBFordiPesysKunStøtterGB`() {

        val journalpostCaptor = argumentCaptor<HttpEntity<Any>>()

        val mapper = jacksonObjectMapper()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json")))
        val requestBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostRequestGB.json")))


        doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        contains("/journalpost?forsoekFerdigstill=false"),
                        any(),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        val journalpostResponse = journalpostService.opprettJournalpost(
                rinaSakId = "1111",
                fnr= "12345678912",
                personNavn= "navn navnesen",
                bucType= "P_BUC_01",
                sedType= "P2000 - Krav om alderspensjon",
                sedHendelseType= "MOTTATT",
                eksternReferanseId= "string",
                kanal= "NAV_NO",
                journalfoerendeEnhet= "9999",
                arkivsaksnummer= "string",
                dokumenter= """
                    [{
                        "brevkode": "NAV 14-05.09",
                        "dokumentKategori": "SOK",
                        "dokumentvarianter": [
                                {
                                    "filtype": "PDF/A",
                                    "fysiskDokument": "string",
                                    "variantformat": "ARKIV"
                                }
                            ],
                            "tittel": "Søknad om foreldrepenger ved fødsel"
                    }]
                """.trimIndent(),
                forsokFerdigstill= false,
                avsenderLand = "UK",
                avsenderNavn = null
        )

        assertEquals(mapper.readTree(requestBody), mapper.readTree(journalpostCaptor.lastValue.body.toString()))
        assertTrue(journalpostResponse!!.toJson() == "{\n" +
                "  \"journalpostId\" : \"429434378\",\n" +
                "  \"journalstatus\" : \"M\",\n" +
                "  \"melding\" : \"null\",\n" +
                "  \"journalpostferdigstilt\" : false\n" +
                "}")
    }

    @Test
    fun `gittJournalpostIdNårOppdaterDistribusjonsinfoSåOppdaterJournalpostMedEESSI`() {

        val journalpostCaptor = argumentCaptor<HttpEntity<Any>>()

        val mapper = jacksonObjectMapper()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val requestBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/oppdaterDistribusjonsinfoRequest.json")))
        val journalpostId = "12345"

        doReturn(
                ResponseEntity.ok(""))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost/{$journalpostId}/oppdaterDistribusjonsinfo"),
                        any(),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        journalpostService.oppdaterDistribusjonsinfo(journalpostId)

        assertEquals(mapper.readTree(requestBody), mapper.readTree(journalpostCaptor.lastValue.body.toString()))
    }
}
