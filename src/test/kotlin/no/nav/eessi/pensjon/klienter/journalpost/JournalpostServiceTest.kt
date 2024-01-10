package no.nav.eessi.pensjon.klienter.journalpost

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.SedType.P2100
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException

internal class JournalpostServiceTest {

    private val mockKlient: JournalpostKlient = mockk(relaxed = true)
    private val journalpostService = JournalpostService(mockKlient)

    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
        private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
    }

    @Test
    fun kanSakFerdigstillesTest() {
        assertFalse(journalpostService.kanSakFerdigstilles(
            OpprettJournalpostRequest(
                null,
                Behandlingstema.ALDERSPENSJON,
                Bruker("brukerId"),
                "[]",
                ID_OG_FORDELING,
                JournalpostType.INNGAAENDE,
                Sak("FAGSAK", "11111", "PEN"),
                Tema.PENSJON,
                emptyList(),
                "tittel på sak"),
                P_BUC_01
        ))

        assertTrue(journalpostService.kanSakFerdigstilles(
            OpprettJournalpostRequest(
                AvsenderMottaker(land = "GB"),
                Behandlingstema.ALDERSPENSJON,
                Bruker("brukerId"),
                "[]",
                ID_OG_FORDELING,
                JournalpostType.INNGAAENDE,
                Sak("FAGSAK", "11111", "PEN"),
                Tema.PENSJON,
                emptyList(),
                "tittel på sak"),
                P_BUC_01
        ))
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val responseBody = getResource("journalpost/opprettJournalpostResponseFalse.json")
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)
        val sedHendelse = sedHendelse(P2000, P_BUC_01, null)

        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any(), any()) } returns expectedResponse

        val actualResponse = journalpostService.opprettJournalpost(
            sedHendelse = sedHendelse,
            fnr = SLAPP_SKILPADDE,
            sedHendelseType = MOTTATT,
            journalfoerendeEnhet = ID_OG_FORDELING,
            arkivsaksnummer = Sak("FAGSAK", "11111", "PEN" ),
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
            saktype = null,
            AvsenderMottaker(null, null, null, land = "NO"),
            1, null, Tema.PENSJON
        )

        // RESPONSE
        assertEqualResponse(expectedResponse, actualResponse!!)

        // REQUEST
        val actualRequest = journalpostSlot.captured

        assertEquals("NO", actualRequest.avsenderMottaker?.land)
        assertNull(actualRequest.avsenderMottaker?.id)
        assertNull(actualRequest.avsenderMottaker?.idType)
        assertNull(actualRequest.avsenderMottaker?.navn)

        assertEquals(Behandlingstema.ALDERSPENSJON, actualRequest.behandlingstema)
        assertEquals(SLAPP_SKILPADDE.toString(), actualRequest.bruker!!.id)
        assertNotNull(actualRequest.dokumenter)
        assertNotNull(actualRequest.eksternReferanseId)
        assertEquals(ID_OG_FORDELING, actualRequest.journalfoerendeEnhet)
        assertEquals(JournalpostType.INNGAAENDE, actualRequest.journalpostType)
        assertEquals("EESSI", actualRequest.kanal)
        assertEquals("11111", actualRequest.sak!!.fagsakid)
        assertEquals("PEN", actualRequest.sak!!.fagsaksystem)
        assertEquals("Inngående P2000 - Krav om alderspensjon", actualRequest.tittel)

        verify(exactly = 1) { mockKlient.opprettJournalpost(any(), true, null) }
    }

    @Test
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        every {
            mockKlient.opprettJournalpost(any(), any(), null)
        } throws HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<RuntimeException> {
            journalpostService.opprettJournalpost(
                sedHendelse = sedHendelse(P2000, P_BUC_01, null),
                fnr = SLAPP_SKILPADDE,
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = ID_OG_FORDELING,
                arkivsaksnummer = Sak("FAGSAK", "11111", "PEN" ),
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
                saktype = null,
                mockk(),
                mockk(),
                mockk(),
                mockk()
            )
        }
    }

    @Test
    fun `Gitt en P2100 med GJENLEV når den er AVSLUTTET Så opprettes en Journalpost uten saknr og til enhet 4303 og tema Pensjon`() {
        val requestSlot = slot<OpprettJournalpostRequest>()
        val responseBody = """{"journalpostId":"429434378","journalstatus":"M","melding":"null","journalpostferdigstilt":false}""".trimIndent()
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)

        val requestBody = """
            {
              "avsenderMottaker" : {
                "id" : "NAV ORG",
                "idType" : "UTL_ORG",
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
              "tittel" : "Utgående P2100",
              "eksternReferanseId" : "647e2336-112f-462f-9f9d-bbfa859fa3ac"
            }
        """.trimIndent()
        val expectedRequest = mapJsonToAny<OpprettJournalpostRequest>(requestBody)

        every { mockKlient.opprettJournalpost(capture(requestSlot), any(), any()) } returns expectedResponse

        val actualResponse = journalpostService.opprettJournalpost(
            sedHendelse = sedHendelse(P2100, P_BUC_02, "NAVT003"),
            fnr = LEALAUS_KAKE,
            sedHendelseType = SENDT,
            journalfoerendeEnhet = ID_OG_FORDELING,
            arkivsaksnummer = null,
            dokumenter = "[\"P2100\"]",
            saktype = null,
            mockk(relaxed = true),
            1,
            null,
            Tema.PENSJON
        )

        assertEqualResponse(expectedResponse, actualResponse!!)

        val actualRequest = requestSlot.captured

        //eksternReferanseId blir auto generert og vil ikke være den samme da dette er forskjellige objekter
        assertNotNull(expectedRequest.eksternReferanseId, actualRequest.eksternReferanseId)
        assertNotEquals(expectedRequest.eksternReferanseId, actualRequest.eksternReferanseId)

        assertEquals(expectedRequest.behandlingstema, actualRequest.behandlingstema)
        assertEquals(expectedRequest.dokumenter, actualRequest.dokumenter)
        assertEquals(expectedRequest.journalfoerendeEnhet, actualRequest.journalfoerendeEnhet)
        assertEquals(expectedRequest.journalpostType, actualRequest.journalpostType)
        assertEquals(expectedRequest.kanal, actualRequest.kanal)
        assertEquals(expectedRequest.tema, actualRequest.tema)
        assertTrue(actualRequest.tittel.contains(expectedRequest.journalpostType.decode()))
        assertEquals(1, actualRequest.tilleggsopplysninger!!.size)

        assertEquals(expectedRequest.sak, actualRequest.sak)
        assertEquals(expectedRequest.bruker!!.id, actualRequest.bruker!!.id)

        verify(exactly = 1) { mockKlient.opprettJournalpost(actualRequest, false, null) }
    }

    @Test
    fun `Gitt JournalpostId Når Oppdater Distribusjonsinfo Så OppdaterJournalpost Med EESSI`() {
        val journalpostId = "12345"

        journalpostService.oppdaterDistribusjonsinfo(journalpostId)

        verify(exactly = 1) { mockKlient.oppdaterDistribusjonsinfo(journalpostId) }
    }

    @Test
    fun `Gitt JournalpostId og ukjent bruker så patcher vi journalposten til Avbrutt`() {
        val journalpostId = "12345"

        journalpostService.settStatusAvbrutt(journalpostId)

        verify(exactly = 1) { mockKlient.settStatusAvbrutt(journalpostId) }
    }

    private fun assertEqualResponse(expected: OpprettJournalPostResponse, actual: OpprettJournalPostResponse) {
        assertEquals(expected.journalpostId, actual.journalpostId)
        assertEquals(expected.journalstatus, actual.journalstatus)
        assertEquals(expected.melding, actual.melding)
        assertEquals(expected.journalpostferdigstilt, actual.journalpostferdigstilt)
    }

    private fun sedHendelse(sedType: SedType, bucType: BucType = P_BUC_01, avsenderNavn: String? = null) = SedHendelse(
        id = 1111,
        bucType = bucType,
        sedType = sedType,
        avsenderLand = "NO",
        avsenderNavn = avsenderNavn,
        sektorKode = "P",
        rinaSakId = "3333",
        rinaDokumentId = "65KJHHG876876656oji7",
        rinaDokumentVersjon = "695654686"
    )
    private fun getResource(path: String): String =
            javaClass.classLoader.getResource(path)!!.readText()
}

