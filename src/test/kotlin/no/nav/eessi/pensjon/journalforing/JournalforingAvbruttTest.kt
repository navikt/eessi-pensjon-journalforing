package no.nav.eessi.pensjon.journalforing

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
@Disabled
internal class JournalforingAvbruttTest : JournalforingServiceBase() {
    @ParameterizedTest
    @EnumSource(SedType::class)
    fun `SED med ukjent fnr settes til avbrutt gitt at den ikke er i listen sedsIkkeTilAvbrutt`(sedType: SedType) {
        val sedHendelse = createMockSedHendelse(sedType, BucType.P_BUC_02)

        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }

        journalfor(sedHendelse, sedType)
        verify(atLeast = 1) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
    }

    @ParameterizedTest
    @EnumSource(BucType::class)
    fun `SED med ukjent fnr settes til avbrutt gitt at den ikke er i listen bucIkkeTilAvbrutt`(buc: BucType) {
        val sedHendelse = createMockSedHendelse(SedType.P8000, buc)

        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }

        journalfor(sedHendelse, SedType.P8000)
        verify(atLeast = 1) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
    }

    @Test
    fun `Gitt JournalpostId og ukjent bruker så patcher vi journalposten til Avbrutt`() {
        val jpId = "12345"
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPDLPerson()

        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(eq("12345")) }

        journalpostService.journalpostSattTilAvbrutt(identifisertPerson.personRelasjon?.fnr, HendelseType.SENDT, sedHendelse, mockk<OpprettJournalPostResponse>().apply {
            every { journalpostId } returns jpId
        })

        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(jpId) }
    }

    @Test
    fun `Sendt sed P2200 med ukjent fnr skal sette status avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(eq("12345")) }

        journalfor(sedHendelse, SedType.P2200)
        Assertions.assertEquals(
            Enhet.ID_OG_FORDELING,
            opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet
        )
        Assertions.assertEquals(
            Behandlingstema.UFOREPENSJON,
            opprettJournalpostRequestCapturingSlot.captured.behandlingstema
        )

        verify { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(journalpostId = "12345") }
    }

    @Test
    fun `Sendt P2000 med ukjent fnr der SED inneholder pesys sakId saa skal status settes til avbrutt og journalpost`() {
        val sedHendelse = createMockSedHendelse(SedType.P2000, BucType.P_BUC_01)
        val oppgaveSlot = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(oppgaveSlot)) }
        justRun {journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }

        journalforingService.journalfor(
            sedHendelse = sedHendelse,
            hendelseType = HendelseType.SENDT,
            identifisertPerson = null,
            fdato = null,
            SaksInfoSamlet(saktype = null,  sakInformasjon = SakInformasjon("12345", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)),
            identifisertePersoner = 0,
            currentSed = null
        )

        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
        verify(exactly = 1) { journalpostKlient.opprettJournalpost(any(), any(), any()) }
    }

    @Test
    fun `Sendt sed P2000 med ukjent fnr SED inneholder IKKE pesys saksId saa skal ikke status settes til avbrutt og journalpost opprettes`() {
        val sedHendelse = createMockSedHendelse(SedType.P2000, BucType.P_BUC_01)

        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            null,
            LEALAUS_KAKE.getBirthDate(),
            null,
            identifisertePersoner = 0,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2000),
        )
        verify(atLeast = 1) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
        verify(atLeast = 1) { journalpostKlient.opprettJournalpost(any(), any(), any()) }
        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
    }

    @Test
    fun `Sendt sed P2200 med ukjent fnr med saksinfo der sakid er null så skal status settes til avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
        val sakInformasjonMock = mockk<SakInformasjon>().apply {
            every { sakId } returns null
            every { sakType } returns SakType.ALDER
        }
        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            null,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(sakInformasjon = sakInformasjonMock),
            identifisertePersoner = 0,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )

        verify(exactly = 1) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
    }


    @Test
    fun `Mottatt sed P2200 med ukjent fnr skal ikke sette status avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPDLPerson()

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 0,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )

        verify(exactly = 0) { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(any()) }
    }

    @Test
    fun `Utgaaende sed P2200 med ukjent fnr skal sette status avbrutt og opprette behandle-sed oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPDLPerson()
        justRun { journalpostKlient.oppdaterJournalpostfeilregistrerSakstilknytning(eq("12345"))}

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 0,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )
        Assertions.assertEquals(
            Enhet.ID_OG_FORDELING,
            opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet
        )
        Assertions.assertEquals(
            Behandlingstema.UFOREPENSJON,
            opprettJournalpostRequestCapturingSlot.captured.behandlingstema
        )

        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any())}
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

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LocalDate.of(1973,11,22),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )
        Assertions.assertEquals(
            Enhet.NFP_UTLAND_AALESUND,
            opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet
        )
        Assertions.assertEquals(
            Behandlingstema.GJENLEVENDEPENSJON,
            opprettJournalpostRequestCapturingSlot.captured.behandlingstema
        )

        verify(exactly = 1) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any())}
    }

    private fun journalfor(sedHendelse: SedHendelse, sedType: SedType, identer: List<IdentifisertPDLPerson> = listOf(identifisertPDLPerson())) {

        journalforingService.journalfor(
            sedHendelse = sedHendelse,
            hendelseType = HendelseType.SENDT,
            identifisertPerson = identer[0],
            fdato = null,
            identifisertePersoner = identer.size,
            currentSed = SED(type = sedType)
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
            every { bucType } returns buc
            every { sedType } returns sed
            every { navBruker } returns bruker
        }
    }
}