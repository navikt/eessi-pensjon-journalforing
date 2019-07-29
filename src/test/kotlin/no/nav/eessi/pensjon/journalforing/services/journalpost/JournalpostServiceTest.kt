package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.argumentCaptor
import org.mockito.Mockito.doReturn
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

    private lateinit var journalpostService: JournalpostService

    @Before
    fun setup() {
        journalpostService = JournalpostService(mockrestTemplate)
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {

        val journalpostCaptor = argumentCaptor<HttpEntity<Any>>()

        val mapper = jacksonObjectMapper()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json")))
        val requestBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json")))


        doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        contains("/journalpost?forsoekFerdigstill=false"),
                        any(),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        val journalpostResponse = journalpostService.opprettJournalpost(
                navBruker= "12345678912",
                personNavn= "navn navnesen",
                avsenderId= "NO:NAVT003",
                avsenderNavn= "NAVT003",
                mottakerId= "NO:NAVT007",
                mottakerNavn= "NAV Test 07",
                bucType= "P_BUC_01",
                sedType= "P2000 - Krav om alderspensjon",
                sedHendelseType= "MOTTATT",
                eksternReferanseId= "string",
                kanal= "NAV_NO",
                journalfoerendeEnhet= "9999",
                arkivsaksnummer= "string",
                arkivsaksystem= "GSAK",
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
                forsokFerdigstill= false
        )

        assertEquals(mapper.readTree(requestBody), mapper.readTree(journalpostCaptor.lastValue.body.toString()))
        assertEquals(journalpostResponse, "429434378")
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
                navBruker= "12345678912",
                personNavn= "navn navnesen",
                avsenderId= "NO:NAVT003",
                avsenderNavn= "NAVT003",
                mottakerId= "NO:NAVT007",
                mottakerNavn= "NAV Test 07",
                bucType= "P_BUC_01",
                sedType= "P2000 - Krav om alderspensjon",
                sedHendelseType= "MOTTATT",
                eksternReferanseId= "string",
                kanal= "NAV_NO",
                journalfoerendeEnhet= "9999",
                arkivsaksnummer= "string",
                arkivsaksystem= "GSAK",
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
                forsokFerdigstill= false
        )
    }
}