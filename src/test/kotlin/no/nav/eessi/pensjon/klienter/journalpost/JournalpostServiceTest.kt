package no.nav.eessi.pensjon.klienter.journalpost

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P15000Pensjon
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.journalforing.JournalpostType.INNGAAENDE
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Behandlingstema.ALDERSPENSJON
import no.nav.eessi.pensjon.models.Behandlingstema.GJENLEVENDEPENSJON
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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
        assertFalse(journalpostService.kanSakFerdigstilles(opprettJournalpostRequest(null),
            P_BUC_01,
            SENDT
        ))

        assertTrue(journalpostService.kanSakFerdigstilles(opprettJournalpostRequest(AvsenderMottaker(land = "GB")),
            P_BUC_01,
            SENDT
        ))
    }

    @Test
    fun `Innkommende P_BUC_02 seder som ikke er p2100 skal kunne ferdigstilles dersom all info for det er tilgjengelig`() {
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val request = opprettJournalpostRequest(null, INNGAAENDE, "EY", EYBARNEP, GJENLEVENDEPENSJON)
        val sedHendelse = sedHendelse(sedType = P2100, P_BUC_02)

        val responseBody = responsebody(false)
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)
        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any(), any()) } returns expectedResponse

        journalpostService.sendJournalPost(request, sedHendelse, MOTTATT, "Line")

        val actualRequest = journalpostSlot.captured
        assertEquals(EYBARNEP, actualRequest.tema)
        assertEquals("EESSI", actualRequest.kanal)
        assertEquals("EY", actualRequest.sak?.fagsaksystem)
        assertEquals( INNGAAENDE, actualRequest.journalpostType)

    }

    @Test
    fun `kanSakFerdigstillesTest skal returnere false dersom det er innkommende P_BUC_02`() {
        assertFalse(journalpostService.kanSakFerdigstilles(
            opprettJournalpostRequest(null),
                P_BUC_02,
                MOTTATT
            )
        )

        //Skal ikke ferdigstille innkommende seder som har gjennytema
        assertFalse(journalpostService.kanSakFerdigstilles(
            opprettJournalpostRequest(tema = OMSTILLING),
            P_BUC_01,
            MOTTATT
        ))

        assertTrue(journalpostService.kanSakFerdigstilles(
            opprettJournalpostRequest(AvsenderMottaker("GB"), tema = OMSTILLING),
                P_BUC_02,
                SENDT
            ))
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val responseBody = responsebody(false)
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)
        val sedHendelse = sedHendelse(P2000, P_BUC_01, null)

        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any(), any()) } returns expectedResponse

        val actualJournalPostRequest = journalpostService.opprettJournalpost(
            sedHendelse,
            SLAPP_SKILPADDE,
            MOTTATT,
            ID_OG_FORDELING,
            Sak("FAGSAK", "11111", "PEN"),
            """
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
            1, null, PENSJON, null
        )
        journalpostService.sendJournalPost(actualJournalPostRequest, sedHendelse, SENDT, "")

        // REQUEST
        val actualRequest = journalpostSlot.captured

        assertEquals("NO", actualRequest.avsenderMottaker?.land)
        assertNull(actualRequest.avsenderMottaker?.id)
        assertNull(actualRequest.avsenderMottaker?.idType)
        assertNull(actualRequest.avsenderMottaker?.navn)

        assertEquals(ALDERSPENSJON, actualRequest.behandlingstema)
        assertEquals(SLAPP_SKILPADDE.toString(), actualRequest.bruker!!.id)
        assertNotNull(actualRequest.dokumenter)
        assertNotNull(actualRequest.eksternReferanseId)
        assertEquals(ID_OG_FORDELING, actualRequest.journalfoerendeEnhet)
        assertEquals(INNGAAENDE, actualRequest.journalpostType)
        assertEquals("EESSI", actualRequest.kanal)
        assertEquals("11111", actualRequest.sak!!.fagsakid)
        assertEquals("PEN", actualRequest.sak!!.fagsaksystem)
        assertEquals("Inngående P2000 - Krav om alderspensjon", actualRequest.tittel)

        verify(exactly = 1) { mockKlient.opprettJournalpost(any(), any(), any()) }
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter med riktig behandlingstema`() {
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val responseBody = responsebody(true)
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)
        val sedHendelse = sedHendelse(P15000, P_BUC_10, null)

        val currentSed = no.nav.eessi.pensjon.eux.model.sed.P15000(type = P15000, pensjon = P15000Pensjon(), nav = Nav(krav = Krav( type =  KravType.GJENLEV)))

        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any(), any()) } returns expectedResponse

        val opprettJournalpost = journalpostService.opprettJournalpost(
            sedHendelse,
            SLAPP_SKILPADDE,
            MOTTATT,
            ID_OG_FORDELING,
            Sak("FAGSAK", "11111", "PEN"),
            """
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
            1, null, PENSJON, currentSed = currentSed
        )

        journalpostService.sendJournalPost(opprettJournalpost, sedHendelse, SENDT, "")

        // REQUEST
        val actualRequest = journalpostSlot.captured
        println("actualResponse ${opprettJournalpost.toJson()}")
        println("actualreq ${actualRequest.toJson()}")
        assertEquals(GJENLEVENDEPENSJON, actualRequest.behandlingstema)
    }

    @Test
    fun `Gitt gyldig argumenter for gjennysak så oppretter vi journalpost med bruker og tema`() {
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        val responseBody = responsebody(true)
        val expectedResponse = mapJsonToAny<OpprettJournalPostResponse>(responseBody)
        val sedHendelse = sedHendelse(P2100, P_BUC_02, null)

        val currentSed = no.nav.eessi.pensjon.eux.model.sed.P2100(type = P2100, pensjon = P15000Pensjon(), nav = Nav(krav = Krav( type =  KravType.GJENLEV)))

        every { mockKlient.opprettJournalpost(capture(journalpostSlot), any(), any()) } returns expectedResponse

        val opprettJournalpost = journalpostService.opprettJournalpost(
            sedHendelse,
            SLAPP_SKILPADDE,
            MOTTATT,
            ID_OG_FORDELING,
            Sak("FAGSAK", "11111", "PEN"),
            """
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
            1, null,
            EYBARNEP, currentSed = currentSed
        )
        journalpostService.sendJournalPost(opprettJournalpost, sedHendelse, SENDT, "")

        // REQUEST
        val actualRequest = journalpostSlot.captured
        println("actualResponse ${opprettJournalpost.toJson()}")
        println("actualreq ${actualRequest.toJson()}")
    }

    @Test
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        every {
            mockKlient.opprettJournalpost(any(), any(), null)
        } throws HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<RuntimeException> {
            journalpostService.opprettJournalpost(
                sedHendelse(P2000, P_BUC_01, null),
                SLAPP_SKILPADDE,
                MOTTATT,
                ID_OG_FORDELING,
                Sak("FAGSAK", "11111", "PEN" ),
                """
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

        val sedHendelse = sedHendelse(P2100, P_BUC_02, "NAVT003")
        val actualResponse = journalpostService.opprettJournalpost(
            sedHendelse,
            LEALAUS_KAKE,
            SENDT,
            ID_OG_FORDELING,
            null,
            "[\"P2100\"]",
            null,
            mockk(relaxed = true),
            1,
            null,
            PENSJON,
            null
        )
        journalpostService.sendJournalPost(actualResponse, sedHendelse, SENDT, "")

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

        //verify(exactly = 1) { mockKlient.opprettJournalpost(actualRequest, false, null) }
    }

    @Test
    fun `Gitt JournalpostId Når Oppdater Distribusjonsinfo Så OppdaterJournalpost Med EESSI`() {
        val journalpostId = "12345"
        journalpostService.oppdaterDistribusjonsinfo(journalpostId)

        verify(exactly = 1) { mockKlient.oppdaterDistribusjonsinfo(journalpostId) }
    }

    private fun responsebody(ferdigstilt: Boolean): String {
        val responseBody = """
                {
                  "journalpostId": "429434378",
                  "journalstatus": "M",
                  "melding": "null",
                  "journalpostferdigstilt": $ferdigstilt,
                  "dokumenter": [
                    {
                      "dokumentInfoId": "453867272"
                    }
                  ]
                }
            """.trimIndent()
        return responseBody
    }

    private fun opprettJournalpostRequest(
        avsenderMottaker: AvsenderMottaker? = AvsenderMottaker(land = "GB"),
        journalpostType: JournalpostType? = INNGAAENDE,
        fagsystem: String ?= "PEN",
        tema: Tema? = PENSJON,
        behandlingstema: Behandlingstema? = ALDERSPENSJON
    ) = OpprettJournalpostRequest(
        avsenderMottaker,
        behandlingstema,
        Bruker("brukerId"),
        "[]",
        ID_OG_FORDELING,
        journalpostType!!,
        Sak("FAGSAK", "11111", fagsystem!!),
        tema!!,
        emptyList(),
        "tittel på sak"
    )

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
}

