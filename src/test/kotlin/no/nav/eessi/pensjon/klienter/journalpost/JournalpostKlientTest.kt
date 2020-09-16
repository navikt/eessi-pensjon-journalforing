package no.nav.eessi.pensjon.klienter.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.*
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class JournalpostKlientTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    private lateinit var journalpostKlient: JournalpostKlient

    @BeforeEach
    fun setup() {
        journalpostKlient = JournalpostKlient(mockrestTemplate)
        journalpostKlient.initMetrics()
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
                        eq(HttpMethod.POST),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        val journalpostResponse = journalpostKlient.opprettJournalpost(
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
                avsenderNavn = null,
                ytelseType = null
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
            journalpostKlient.opprettJournalpost(
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
                    avsenderNavn = null,
                    ytelseType = null
            )
        }
    }

    @Test
    fun `gitt En UK Journalpost Så Bytt Til GB Fordi Pesys Kun Støtter GB`() {

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

        val journalpostResponse = journalpostKlient.opprettJournalpost(
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
                avsenderLand = "UK",
                avsenderNavn = null,
                ytelseType = null
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
    fun `Gitt en P2100 med UFØREP Når den er AVSLUTTET Så opprettes en Journalpost uten saknr og til enhet 4303 og tema Pensjon`() {
        ReflectionTestUtils.setField(journalpostKlient, "navOrgnummer", "NAV ORG" )

        val journalpostCaptor = argumentCaptor<HttpEntity<Any>>()
        val mapper = jacksonObjectMapper()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val responseBody = """{"journalpostId":"429434378","journalstatus":"M","melding":"null","journalpostferdigstilt":false}""".trimIndent()
        val requestBody = """
            {
              "avsenderMottaker" : {
                "id" : "NAV ORG",
                "idType" : "ORGNR",
                "navn" : "NAV",
                "land" : "NO"
              },
              "behandlingstema" : "ab0011",
              "bruker" : {
                "id" : "12078945602",
                "idType" : "FNR"
              },
              "dokumenter" : ["P2100"],
              "journalfoerendeEnhet" : "4303",
              "journalpostType" : "UTGAAENDE",
              "kanal" : "EESSI",
              "tema" : "PEN",
              "tilleggsopplysninger" : [ {
                "nokkel" : "eessi_pensjon_bucid",
                "verdi" : "147730"
              } ],
              "tittel" : "Utgående P2100"
            }
        """.trimIndent()


        doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        contains("/journalpost?forsoekFerdigstill=false"),
                        any(),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        val journalpostResponse = journalpostKlient.opprettJournalpost(
            rinaSakId = "147730",
            fnr = "12078945602",
            personNavn = "Test Testesen",
            bucType = "P_BUC_02",
            sedType = SedType.P2100.name,
            sedHendelseType = "SENDT",
            eksternReferanseId = null,
            kanal = "EESSI",
            journalfoerendeEnhet = "4303",
            arkivsaksnummer = null,
            dokumenter = "[\"P2100\"]",
            forsokFerdigstill = false,
            avsenderLand = "NO",
            avsenderNavn = "NAVT003",
            ytelseType = null
        )
        assertEquals(mapper.readTree(requestBody), mapper.readTree(journalpostCaptor.lastValue.body.toString()))
        assertEquals(responseBody, journalpostResponse.toString())

    }


    @Test
    fun `Gitt JournalpostId Når Oppdater Distribusjonsinfo Så OppdaterJournalpost Med EESSI`() {

        val journalpostCaptor = argumentCaptor<HttpEntity<Any>>()

        val mapper = jacksonObjectMapper()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val requestBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/oppdaterDistribusjonsinfoRequest.json")))
        val journalpostId = "12345"

        doReturn(
                ResponseEntity.ok(""))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost/$journalpostId/oppdaterDistribusjonsinfo"),
                        any(),
                        journalpostCaptor.capture(),
                        eq(String::class.java))

        journalpostKlient.oppdaterDistribusjonsinfo(journalpostId)

        assertEquals(mapper.readTree(requestBody), mapper.readTree(journalpostCaptor.lastValue.body.toString()))
    }

    @Test
    fun `gitt det er en P_BUC_02 med ytelsetype BARNEP så skal det settes teama PEN`() {
        val result = journalpostKlient.hentTema("P_BUC_02", "P2100", "1212", YtelseType.BARNEP)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med ytelsetype UFOREP så skal det settes teama UFO`() {
        val result = journalpostKlient.hentTema("P_BUC_02", "P2100", "1212", YtelseType.UFOREP)
        assertEquals("UFO", result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med ytelsetype GJENLEVENDE så skal det settes teama PEN`() {
        val result = journalpostKlient.hentTema("P_BUC_02", "P2100", "1212", YtelseType.GJENLEV)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en P_BUC_01 med ytelsetype ALDER så skal det settes teama PEN`() {
        val result = journalpostKlient.hentTema("P_BUC_01", "P6000", "1212", null)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en R_BUC_02 og sed er R004 og enhet er 4819 så skal det settes teama PEN`() {
        val result = journalpostKlient.hentTema("R_BUC_02", "R004", "4819", YtelseType.ALDER)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er UFOREP så skal det settes teama UFO`() {
        val result = journalpostKlient.hentTema("R_BUC_02", "R006", "4819", YtelseType.UFOREP)
        assertEquals("UFO", result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er ALDER så skal det settes teama PEN`() {
        val result = journalpostKlient.hentTema("R_BUC_02", "R006", "4819", YtelseType.ALDER)
        assertEquals("PEN", result)
    }


}
