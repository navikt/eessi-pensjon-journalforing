package no.nav.eessi.pensjon.klienter.journalpost

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.HttpServerErrorException
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
internal class JournalpostServiceTest {

    @Mock
    private lateinit var journalpostKlient: JournalpostKlient

    private lateinit var journalpostService: JournalpostService

    @BeforeEach
    fun setup() {
        journalpostService = JournalpostService(journalpostKlient)
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {

        val journalpostCaptor = argumentCaptor<OpprettJournalpostRequest>()

        val responseBody = getResource("journalpost/opprettJournalpostResponse.json")
        val response = mapJsonToAny(responseBody, typeRefs<OpprettJournalPostResponse>())

        doReturn(response).`when`(journalpostKlient).opprettJournalpost(journalpostCaptor.capture(), anyBoolean())

        val actualResponse = journalpostService.opprettJournalpost(
                rinaSakId = "1111",
                fnr = "12345678912",
                personNavn = "navn navnesen",
                bucType = "P_BUC_01",
                sedType = "P2000 - Krav om alderspensjon",
                sedHendelseType = "MOTTATT",
                eksternReferanseId = "en-ekstern-ref-id",
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

        // REQUEST
        val actualRequest = journalpostCaptor.firstValue

        assertEquals("NO", actualRequest.avsenderMottaker.land)
        assertNull(actualRequest.avsenderMottaker.id)
        assertNull(actualRequest.avsenderMottaker.idType)
        assertNull(actualRequest.avsenderMottaker.navn)

        assertEquals("ab0254", actualRequest.behandlingstema)
        assertEquals("12345678912", actualRequest.bruker!!.id)
        assertNotNull(actualRequest.dokumenter)
        assertEquals("en-ekstern-ref-id", actualRequest.eksternReferanseId)
        assertEquals("9999", actualRequest.journalfoerendeEnhet)
        assertEquals(JournalpostType.INNGAAENDE, actualRequest.journalpostType)
        assertEquals("NAV_NO", actualRequest.kanal)
        assertEquals("string", actualRequest.sak!!.arkivsaksnummer)
        assertEquals("PSAK", actualRequest.sak!!.arkivsaksystem)
        assertEquals("Inngående P2000 - Krav om alderspensjon", actualRequest.tittel)

        // RESPONSE
        assertEquals(response.journalpostId, actualResponse!!.journalpostId)
        assertEquals(response.journalstatus, actualResponse.journalstatus)
        assertEquals(response.melding, actualResponse.melding)
        assertEquals(response.journalpostferdigstilt, actualResponse.journalpostferdigstilt)
    }

    @Test
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .`when`(journalpostKlient).opprettJournalpost(any(), any())

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
                    avsenderNavn = null,
                    ytelseType = null
            )
        }
    }

    @Test
    fun `gitt En UK Journalpost Så Bytt Til GB Fordi Pesys Kun Støtter GB`() {
        val journalpostCaptor = argumentCaptor<OpprettJournalpostRequest>()

        val responseJson = getResource("journalpost/opprettJournalpostResponse.json")
        val response = mapJsonToAny(responseJson, typeRefs<OpprettJournalPostResponse>())

        val requestJson = getResource("journalpost/opprettJournalpostRequestGB.json")
        val request = mapJsonToAny(requestJson, typeRefs<OpprettJournalpostRequest>())

        doReturn(response)
                .`when`(journalpostKlient).opprettJournalpost(journalpostCaptor.capture(), any())

        val actualResponse = journalpostService.opprettJournalpost(
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
                     [{"brevkode":"NAV 14-05.09","dokumentKategori":"SOK","dokumentvarianter":[{"filtype":"PDF/A","fysiskDokument":"string","variantformat":"ARKIV"}],"tittel":"Søknad om foreldrepenger ved fødsel"}]""".trimIndent(),
                forsokFerdigstill = false,
                avsenderLand = "UK",
                avsenderNavn = null,
                ytelseType = null
        )

        assertEquals(response, actualResponse)

        val actualRequest = journalpostCaptor.lastValue

        assertEquals(request.behandlingstema, actualRequest.behandlingstema)
        assertEquals(request.dokumenter, actualRequest.dokumenter)
        assertEquals(request.eksternReferanseId, actualRequest.eksternReferanseId)
        assertEquals(request.journalfoerendeEnhet, actualRequest.journalfoerendeEnhet)
        assertEquals(request.journalpostType, actualRequest.journalpostType)
        assertEquals(request.kanal, actualRequest.kanal)
        assertEquals(request.tema, actualRequest.tema)
//        assertEquals(request.tilleggsopplysninger, actualRequest.tilleggsopplysninger)
        assertEquals(request.tittel, actualRequest.tittel)

        assertEquals(request.sak!!.arkivsaksnummer, actualRequest.sak!!.arkivsaksnummer)
        assertEquals(request.sak!!.arkivsaksystem, actualRequest.sak!!.arkivsaksystem)

        assertEquals(request.bruker!!.id, actualRequest.bruker!!.id)
        assertEquals("GB", actualRequest.avsenderMottaker.land)
    }

    @Test
    fun `Gitt en P2100 med UFØREP Når den er AVSLUTTET Så opprettes en Journalpost uten saknr og til enhet 4303 og tema Pensjon`() {
        ReflectionTestUtils.setField(journalpostService, "navOrgnummer", "NAV ORG" )

        val journalpostCaptor = argumentCaptor<OpprettJournalpostRequest>()

        val responseBody = """{"journalpostId":"429434378","journalstatus":"M","melding":"null","journalpostferdigstilt":false}""".trimIndent()
        val expectedResponse = mapJsonToAny(responseBody, typeRefs<OpprettJournalPostResponse>())

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
        val expectedRequest = mapJsonToAny(requestBody, typeRefs<OpprettJournalpostRequest>())

        doReturn(expectedResponse).`when`(journalpostKlient).opprettJournalpost(journalpostCaptor.capture(), any())

        val forsokFerdigstill = false
        val actualResponse = journalpostService.opprettJournalpost(
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
            forsokFerdigstill = forsokFerdigstill,
            avsenderLand = "NO",
            avsenderNavn = "NAVT003",
            ytelseType = null
        )

        val actualRequest = journalpostCaptor.lastValue

        assertEquals(expectedRequest.behandlingstema, actualRequest.behandlingstema)
        assertEquals(expectedRequest.dokumenter, actualRequest.dokumenter)
        assertEquals(expectedRequest.eksternReferanseId, actualRequest.eksternReferanseId)
        assertEquals(expectedRequest.journalfoerendeEnhet, actualRequest.journalfoerendeEnhet)
        assertEquals(expectedRequest.journalpostType, actualRequest.journalpostType)
        assertEquals(expectedRequest.kanal, actualRequest.kanal)
        assertEquals(expectedRequest.tema, actualRequest.tema)
//        assertEquals(expectedRequest.tilleggsopplysninger, actualRequest.tilleggsopplysninger)
        assertEquals(expectedRequest.tittel, actualRequest.tittel)

        assertEquals(expectedRequest.sak, actualRequest.sak)

        assertEquals(expectedRequest.bruker!!.id, actualRequest.bruker!!.id)
        assertEquals("NO", actualRequest.avsenderMottaker.land)

        verify(journalpostKlient).opprettJournalpost(actualRequest, forsokFerdigstill)
    }

    @Test
    fun `Gitt JournalpostId Når Oppdater Distribusjonsinfo Så OppdaterJournalpost Med EESSI`() {
        val journalpostId = "12345"

        journalpostService.oppdaterDistribusjonsinfo(journalpostId)

        verify(journalpostKlient).oppdaterDistribusjonsinfo(journalpostId)
    }

    @Test
    fun `gitt det er en P_BUC_02 med ytelsetype BARNEP så skal det settes teama PEN`() {
        val result = journalpostService.hentTema("P_BUC_02", "P2100", "1212", YtelseType.BARNEP)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med ytelsetype UFOREP så skal det settes teama UFO`() {
        val result = journalpostService.hentTema("P_BUC_02", "P2100", "1212", YtelseType.UFOREP)
        assertEquals("UFO", result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med ytelsetype GJENLEVENDE så skal det settes teama PEN`() {
        val result = journalpostService.hentTema("P_BUC_02", "P2100", "1212", YtelseType.GJENLEV)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en P_BUC_01 med ytelsetype ALDER så skal det settes teama PEN`() {
        val result = journalpostService.hentTema("P_BUC_01", "P6000", "1212", null)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en R_BUC_02 og sed er R004 og enhet er 4819 så skal det settes teama PEN`() {
        val result = journalpostService.hentTema("R_BUC_02", "R004", "4819", YtelseType.ALDER)
        assertEquals("PEN", result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er UFOREP så skal det settes teama UFO`() {
        val result = journalpostService.hentTema("R_BUC_02", "R006", "4819", YtelseType.UFOREP)
        assertEquals("UFO", result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er ALDER så skal det settes teama PEN`() {
        val result = journalpostService.hentTema("R_BUC_02", "R006", "4819", YtelseType.ALDER)
        assertEquals("PEN", result)
    }


    private fun getResource(path: String): String =
            javaClass.classLoader.getResource(path)!!.readText()
}

