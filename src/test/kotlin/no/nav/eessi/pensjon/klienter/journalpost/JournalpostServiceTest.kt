package no.nav.eessi.pensjon.klienter.journalpost

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.Saktype.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.HttpServerErrorException

internal class JournalpostServiceTest {

    private val mockKlient: JournalpostKlient = mockk(relaxed = true)

    private val journalpostService = JournalpostService(mockKlient)

    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
        private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {

        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val responseBody = getResource("journalpost/opprettJournalpostResponseFalse.json")
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)

        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any()) } returns expectedResponse

        val actualResponse = journalpostService.opprettJournalpost(
            rinaSakId = "1111",
            fnr = SLAPP_SKILPADDE,
            bucType = BucType.P_BUC_01,
            sedType = SedType.P2000,
            sedHendelseType = HendelseType.MOTTATT,
            journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
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
            avsenderLand = "NO",
            avsenderNavn = null,
            saktype = null
        )

        // RESPONSE
        assertEqualResponse(expectedResponse, actualResponse!!)

        // REQUEST
        val actualRequest = journalpostSlot.captured

        assertEquals("NO", actualRequest.avsenderMottaker.land)
        assertNull(actualRequest.avsenderMottaker.id)
        assertNull(actualRequest.avsenderMottaker.idType)
        assertNull(actualRequest.avsenderMottaker.navn)

        assertEquals(Behandlingstema.ALDERSPENSJON, actualRequest.behandlingstema)
        assertEquals(SLAPP_SKILPADDE.toString(), actualRequest.bruker!!.id)
        assertNotNull(actualRequest.dokumenter)
        assertNull(actualRequest.eksternReferanseId)
        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, actualRequest.journalfoerendeEnhet)
        assertEquals(JournalpostType.INNGAAENDE, actualRequest.journalpostType)
        assertEquals("EESSI", actualRequest.kanal)
        assertEquals("string", actualRequest.sak!!.arkivsaksnummer)
        assertEquals("PSAK", actualRequest.sak!!.arkivsaksystem)
        assertEquals("Inngående P2000 - Krav om alderspensjon", actualRequest.tittel)

        verify(exactly = 1) { mockKlient.opprettJournalpost(any(), true) }
    }

    @Test
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        every {
            mockKlient.opprettJournalpost(any(), any())
        } throws HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<RuntimeException> {
            journalpostService.opprettJournalpost(
                rinaSakId = "1111",
                fnr = SLAPP_SKILPADDE,
                bucType = BucType.P_BUC_01,
                sedType = SedType.P2000,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
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
                avsenderLand = "NO",
                avsenderNavn = null,
                saktype = null
            )
        }
    }

    @Test
    fun `gitt En UK Journalpost Så Bytt Til GB Fordi Pesys Kun Støtter GB`() {
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val responseJson = getResource("journalpost/opprettJournalpostResponseFalse.json")
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseJson)

        val requestJson = getResource("journalpost/opprettJournalpostRequestGB.json")
        val request = mapJsonToAny<OpprettJournalpostRequest>(requestJson)

        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any()) } returns expectedResponse

        val actualResponse = journalpostService.opprettJournalpost(
            rinaSakId = "1111",
            fnr = SLAPP_SKILPADDE,
            bucType = BucType.P_BUC_01,
            sedType = SedType.P2000,
            sedHendelseType = HendelseType.MOTTATT,
            journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
            arkivsaksnummer = "string",
            dokumenter = """
                 [{"brevkode":"NAV 14-05.09","dokumentKategori":"SOK","dokumentvarianter":[{"filtype":"PDF/A","fysiskDokument":"string","variantformat":"ARKIV"}],"tittel":"Søknad om foreldrepenger ved fødsel"}]""".trimIndent(),
            avsenderLand = "UK",
            avsenderNavn = null,
            saktype = null
        )

        assertEqualResponse(expectedResponse, actualResponse!!)

        val actualRequest = journalpostSlot.captured

        assertEquals(request.behandlingstema, actualRequest.behandlingstema)
        assertEquals(request.dokumenter, actualRequest.dokumenter)
        assertNull(actualRequest.eksternReferanseId)
        assertEquals(request.journalfoerendeEnhet, actualRequest.journalfoerendeEnhet)
        assertEquals(request.journalpostType, actualRequest.journalpostType)
        assertEquals("EESSI", actualRequest.kanal)
        assertEquals(request.tema, actualRequest.tema)
        assertEquals(1, actualRequest.tilleggsopplysninger!!.size)
        assertEquals(request.tittel, actualRequest.tittel)

        assertEquals(request.sak!!.arkivsaksnummer, actualRequest.sak!!.arkivsaksnummer)
        assertEquals(request.sak!!.arkivsaksystem, actualRequest.sak!!.arkivsaksystem)

        assertEquals(request.bruker!!.id, actualRequest.bruker!!.id)
        assertEquals("GB", actualRequest.avsenderMottaker.land)

        verify(exactly = 1) { mockKlient.opprettJournalpost(any(), true) }
    }

    @Test
    fun `Gitt en P2100 med UFØREP Når den er AVSLUTTET Så opprettes en Journalpost uten saknr og til enhet 4303 og tema Pensjon`() {
        ReflectionTestUtils.setField(journalpostService, "navOrgnummer", "NAV ORG")

        val requestSlot = slot<OpprettJournalpostRequest>()

        val responseBody = """{"journalpostId":"429434378","journalstatus":"M","melding":"null","journalpostferdigstilt":false}""".trimIndent()
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)

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
                "id" : $LEALAUS_KAKE,
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
        val expectedRequest = mapJsonToAny<OpprettJournalpostRequest>(requestBody)

        every { mockKlient.opprettJournalpost(capture(requestSlot), any()) } returns expectedResponse

        val actualResponse = journalpostService.opprettJournalpost(
            rinaSakId = "147730",
            fnr = LEALAUS_KAKE,
            bucType = BucType.P_BUC_02,
            sedType = SedType.P2100,
            sedHendelseType = HendelseType.SENDT,
            journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
            arkivsaksnummer = null,
            dokumenter = "[\"P2100\"]",
            avsenderLand = "NO",
            avsenderNavn = "NAVT003",
            saktype = null
        )

        assertEqualResponse(expectedResponse, actualResponse!!)

        val actualRequest = requestSlot.captured

        assertEquals(expectedRequest.behandlingstema, actualRequest.behandlingstema)
        assertEquals(expectedRequest.dokumenter, actualRequest.dokumenter)
        assertEquals(expectedRequest.eksternReferanseId, actualRequest.eksternReferanseId)
        assertEquals(expectedRequest.journalfoerendeEnhet, actualRequest.journalfoerendeEnhet)
        assertEquals(expectedRequest.journalpostType, actualRequest.journalpostType)
        assertEquals(expectedRequest.kanal, actualRequest.kanal)
        assertEquals(expectedRequest.tema, actualRequest.tema)
        assertTrue(actualRequest.tittel.contains(expectedRequest.journalpostType.decode()))
        assertEquals(1, actualRequest.tilleggsopplysninger!!.size)

        assertEquals(expectedRequest.sak, actualRequest.sak)

        assertEquals(expectedRequest.bruker!!.id, actualRequest.bruker!!.id)
        assertEquals("NO", actualRequest.avsenderMottaker.land)

        verify(exactly = 1) { mockKlient.opprettJournalpost(actualRequest, false) }
    }

    @Test
    fun `Gitt JournalpostId Når Oppdater Distribusjonsinfo Så OppdaterJournalpost Med EESSI`() {
        val journalpostId = "12345"

        journalpostService.oppdaterDistribusjonsinfo(journalpostId)

        verify(exactly = 1) { mockKlient.oppdaterDistribusjonsinfo(journalpostId) }
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype BARNEP så skal det settes teama PEN`() {
        val result = journalpostService.hentTema(BucType.P_BUC_02, BARNEP)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype UFOREP så skal det settes teama UFO`() {
        val result = journalpostService.hentTema(BucType.P_BUC_02, UFOREP)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype GJENLEVENDE så skal det settes teama PEN`() {
        val result = journalpostService.hentTema(BucType.P_BUC_02, GJENLEV)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_01 med saktype ALDER så skal det settes teama PEN`() {
        val result = journalpostService.hentTema(BucType.P_BUC_01, null)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 og sed er R004 og enhet er 4819 så skal det settes teama PEN`() {
        val result = journalpostService.hentTema(BucType.R_BUC_02, ALDER)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er UFOREP så skal det settes teama UFO`() {
        val result = journalpostService.hentTema(BucType.R_BUC_02, UFOREP)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er ALDER så skal det settes teama PEN`() {
        val result = journalpostService.hentTema(BucType.R_BUC_02, ALDER)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype IKKE er UFOREP så skal det settes teama PEN`() {
        val resultatGENRL = journalpostService.hentTema(BucType.P_BUC_05, GENRL)
        assertEquals(Tema.PENSJON, resultatGENRL)

        val resultatOMSORG = journalpostService.hentTema(BucType.P_BUC_05, OMSORG)
        assertEquals(Tema.PENSJON, resultatOMSORG)

        val resultatALDER = journalpostService.hentTema(BucType.P_BUC_05, ALDER)
        assertEquals(Tema.PENSJON, resultatALDER)

        val resultatGJENLEV = journalpostService.hentTema(BucType.P_BUC_05, GJENLEV)
        assertEquals(Tema.PENSJON, resultatGJENLEV)

        val resultatBARNEP = journalpostService.hentTema(BucType.P_BUC_05, BARNEP)
        assertEquals(Tema.PENSJON, resultatBARNEP)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype er UFOREP så skal det settes teama UFO`() {
        val result = journalpostService.hentTema(BucType.P_BUC_05, UFOREP)
        assertEquals(Tema.UFORETRYGD, result)
    }

    private fun assertEqualResponse(expected: OpprettJournalPostResponse, actual: OpprettJournalPostResponse) {
        assertEquals(expected.journalpostId, actual.journalpostId)
        assertEquals(expected.journalstatus, actual.journalstatus)
        assertEquals(expected.melding, actual.melding)
        assertEquals(expected.journalpostferdigstilt, actual.journalpostferdigstilt)
    }

    private fun getResource(path: String): String =
            javaClass.classLoader.getResource(path)!!.readText()
}

