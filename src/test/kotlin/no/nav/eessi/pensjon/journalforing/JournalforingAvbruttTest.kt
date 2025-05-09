package no.nav.eessi.pensjon.journalforing

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.shared.person.FodselsnummerGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!

internal class JournalforingAvbruttTest : JournalforingServiceBase() {

    @ParameterizedTest
    @EnumSource(SedType::class)
    fun `SED med ukjent fnr settes til avbrutt gitt at den ikke er i listen sedsIkkeTilAvbrutt`(sedType: SedType) {
        val sedHendelse = createMockSedHendelse(sedType, BucType.P_BUC_02)

        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }

        journalManueltMedAvbrutt(sedHendelse, HendelseType.SENDT)
        if (sedType in listOf(SedType.R004, SedType.R005, SedType.R006)) {
            verify(atLeast = 0) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
        } else verify(atLeast = 1) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
    }

    @ParameterizedTest
    @EnumSource(BucType::class)
    fun `SED med ukjent fnr settes til avbrutt gitt at den ikke er i listen bucIkkeTilAvbrutt`(buc: BucType) {
        val sedHendelse = createMockSedHendelse(SedType.P8000, buc)

        every { journalpostKlient.opprettJournalpost(any(), any(), any()) } returns
                mockk<OpprettJournalPostResponse>(relaxed = true).apply {
                    every { journalpostId } returns "12345"
                    every { journalpostferdigstilt } returns false
                }
        journalfor(sedHendelse, HendelseType.SENDT)
        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
    }

    @Test
    fun `Sendt P2200 med ukjent fnr skal sette status avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val journapostId = "112233"
        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(eq(sedHendelse.rinaSakId)) }

        journalManueltMedAvbrutt(sedHendelse, HendelseType.SENDT, journalPostIdMock = journapostId)

        assertEquals(Enhet.ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)

        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostMedAvbrutt(journalpostId = journapostId) }
    }

    @Test
    fun `Sendt P2000 med ukjent fnr der SED inneholder pesys sakId saa skal status settes til avbrutt`() {
        val sedHendelse = createMockSedHendelse(SedType.P2000, BucType.P_BUC_01)
        val oppgaveSlot = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(oppgaveSlot)) }
        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }

        journalfor(sedHendelse, HendelseType.SENDT)

        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
    }

    @Test
    fun `Sendt sed P2000 med ukjent fnr SED inneholder IKKE pesys saksId saa skal ikke status settes til avbrutt og journalpost opprettes`() {
        val sedHendelse = createMockSedHendelse(SedType.P2000, BucType.P_BUC_01)

        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }

        val journapostId = "112233"
        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(eq(sedHendelse.rinaSakId)) }
        journalManueltMedAvbrutt(sedHendelse, HendelseType.SENDT, journalPostIdMock = journapostId)

        verify(atLeast = 1) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
    }

    @Test
    fun `Sendt sed P2200 med ukjent fnr med saksinfo der sakid er null så skal status settes til avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPDLPerson()

        val sakInformasjonMock = mockk<SakInformasjon>().apply {
            every { sakId } returns null
            every { sakType } returns SakType.ALDER
        }

        journalfor(
            sedHendelse = sedHendelse,
            hendelseType = HendelseType.SENDT,
            identer = listOf(identifisertPerson),
            saksInfoSamlet = SaksInfoSamlet(sakInformasjonFraPesys = sakInformasjonMock)
        )

        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
    }


    @Test
    fun `Mottatt sed P2200 med ukjent fnr skal ikke sette status avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPDLPerson()

        journalfor(sedHendelse, HendelseType.MOTTATT, listOf(identifisertPerson))

        verify(exactly = 0) { journalpostKlient.oppdaterJournalpostMedAvbrutt(any()) }
    }

    @Test
    fun `Sendt sed P2200 med ukjent fnr skal sette status avbrutt og opprette behandle-sed oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPDLPerson()

        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(eq(sedHendelse.rinaSakId)) }

        journalManueltMedAvbrutt(
            sedHendelse,
            HendelseType.SENDT,
            journalPostIdMock = "112233",
            identer = listOf(identifisertPerson)
        )

        assertEquals(
            Enhet.ID_OG_FORDELING,
            opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet
        )
        assertEquals(
            Behandlingstema.UFOREPENSJON,
            opprettJournalpostRequestCapturingSlot.captured.behandlingstema
        )

        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
    }

    @Test
    fun `Utgaaende sed P2200 med kjent fnr skal status ikke settes til avbrutt og vi skal opprette journalfoerings oppgave`() {
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P2100_b12e06dda2c7474b9998c7139c841646_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "147730",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "b12e06dda2c7474b9998c7139c841646",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "22117320034"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(Fodselsnummer.fra("22117320034"), Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )
        every { gcpStorageService.hentFraGjenny(sedHendelse.rinaSakId) } returns null
        every { gcpStorageService.gjennyFinnes(any()) } returns false

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LocalDate.of(1973, 11, 22),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )
        //journalpostService.sendJournalPost(opprettJournalpostRequestCapturingSlot.captured, sedHendelse, HendelseType.SENDT, "ident")
        assertEquals(
            Enhet.NFP_UTLAND_AALESUND,
            opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet
        )
        assertEquals(
            Behandlingstema.GJENLEVENDEPENSJON,
            opprettJournalpostRequestCapturingSlot.captured.behandlingstema
        )

        verify(exactly = 1) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
    }

    @Test
    fun `Utgaaende sed P2100 med kjent fnr skal status ikke settes til avbrutt og vi skal opprette journalfoerings oppgave`() {
        val fnr = FodselsnummerGenerator.generateFnrForTest(68)
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P2100_b12e06dda2c7474b9998c7139c841646_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "147730",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "b12e06dda2c7474b9998c7139c841646",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "$fnr"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(Fodselsnummer.fra(fnr), Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID, fdato = LocalDate.of(1947, 11, 22)),
            "NOR",

        )
        every { gcpStorageService.hentFraGjenny(sedHendelse.rinaSakId) } returns "EYO"

        assertThrows<Exception> {
            journalforingService.journalfor(
                sedHendelse = sedHendelse,
                hendelseType = HendelseType.SENDT,
                identifisertPerson = identifisertPerson,
                fdato = LocalDate.of(1947, 11, 22),
                identifisertePersoner = 1,
                navAnsattInfo = null,
                currentSed = SED(type = SedType.P2200)
            )
        }

        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
    }


    private fun journalManueltMedAvbrutt(
        sedHendelse: SedHendelse,
        hendelseType: HendelseType = HendelseType.SENDT,
        identer: List<IdentifisertPDLPerson> = listOf(identifisertPDLPerson()),
        journalPostIdMock: String = "112233",
        saksInfoSamlet: SaksInfoSamlet? = null
    ) {

        journalfor(
            sedHendelse = sedHendelse,
            hendelseType = hendelseType,
            identer = identer,
            saksInfoSamlet = saksInfoSamlet
        )

        journalforingService.vurderSettAvbruttOgLagOppgave(
            fnr = identer[0].fnr,
            hendelseType = hendelseType,
            sedHendelse = sedHendelse,
            mockk<OpprettJournalPostResponse>(relaxed = true).apply {
                every { journalpostId } returns journalPostIdMock
            },
            Enhet.ID_OG_FORDELING,
            identer.first().fdato.toString(),
            Tema.OMSTILLING,
        )
    }

    private fun journalfor(
        sedHendelse: SedHendelse,
        hendelseType: HendelseType = HendelseType.SENDT,
        identer: List<IdentifisertPDLPerson> = listOf(identifisertPDLPerson()),
        saksInfoSamlet: SaksInfoSamlet? = null
    ) {

        journalforingService.journalfor(
            sedHendelse,
            hendelseType,
            identer.first(),
            LocalDate.of(1973, 11, 22),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            saksInfoSamlet = saksInfoSamlet,
            currentSed = SED(type = SedType.P2200)
        )
    }

    private fun identifisertPDLPerson(): IdentifisertPDLPerson {
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )
        return identifisertPerson
    }

    fun createMockSedHendelse(sed: SedType, buc: BucType, bruker: Fodselsnummer = LEALAUS_KAKE): SedHendelse {
        return mockk<SedHendelse>(relaxed = true).apply {
            every { rinaSakId } returns "223344"
            every { bucType } returns buc
            every { sedType } returns sed
            every { navBruker } returns bruker
        }
    }

}