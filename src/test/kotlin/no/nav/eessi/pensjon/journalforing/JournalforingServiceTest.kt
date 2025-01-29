package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"

internal class JournalforingServiceTest : JournalforingServiceBase() {

    private val fdato = LocalDate.now()
    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
        private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
        private val STERK_BUSK = Fodselsnummer.fra("12011577847")!!
    }

    @BeforeEach
    fun setupClass() {
        every { gcpStorageService.hentFraGjenny(any()) } returns null
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 og sakType er OMSORG saa skal det ikke opprettes en journalforingsoppgave`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            fnr = Fodselsnummer.fra("22117320034"),
            aktoerId = AKTOERID,
            personRelasjon = sedPersonRelasjon(fnr = Fodselsnummer.fra("22117320034")),
            landkode = "NO"
        )

        val gjennysakGcp = """
            {
              "sakId" : "147730",
              "sakType" : "EYO"
            }
        """.trimIndent()

        every { gcpStorageService.gjennyFinnes(any()) } returns true
        every { gcpStorageService.hentFraGjenny(any()) } returns gjennysakGcp

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LocalDate.of(1973, 11,22),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )

        assertEquals(Tema.OMSTILLING, opprettJournalpostRequestCapturingSlot.captured.tema)
    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = ALDER),
            false,
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(
                type = SedType.R004,
            )
        )
        assertEquals(OKONOMI_PENSJON, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }


    @Test
    fun `Ved mottatt P2000 som kan automatisk ferdigstilles så skal det opprettes en Behandle SED oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_SE.json")!!.readText()
        val sed = mapJsonToAny<P2000>(javaClass.getResource("/sed/P2000-NAV.json")!!.readText())
        val sedHendelse = SedHendelse.fromJson(hendelse).copy(bucType = P_BUC_01)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = ALDER),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = sed
        )

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "P2000",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "PEN"
              }""".trimIndent()
        )
        verify { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }
    }

    @Test
    fun `Ved mottatt P2100 som det skal opprettes gjennyOppgave for skal det opprettes en journalforing oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_02_P2100.json")!!.readText()
        val sed = mapJsonToAny<P2100>(javaClass.getResource("/sed/P2100.json")!!.readText())
        val sedHendelse = SedHendelse.fromJson(hendelse).copy(bucType = P_BUC_02)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        every { gcpStorageService.hentFraGjenny(any()) } returns """{"sakId":"147729","sakType":"EYO"}"""

        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = OMSORG),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = sed
        )

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "P2100",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "EYO"
              }""".trimIndent()
        )
        verify { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }
    }

    @Test
    fun `Ved mottatt P2100 som kan automatisk ferdigstilles så skal det opprettes en Behandle SED oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_02_P2100_SE.json")!!.readText()
        val sed = mapJsonToAny<P2100>(javaClass.getResource("/sed/P2100.json")!!.readText())
        val sedHendelse = SedHendelse.fromJson(hendelse).copy(bucType = P_BUC_02)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }
        justRun { journalpostKlient.oppdaterJournalpostMedAvbrutt(eq("12345")) }
        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = GJENLEV),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = sed
        )

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "P2100",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "PEN"
              }""".trimIndent()
        )
        verify(exactly = 1) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }
    }

    @Test
    fun `Sendt gyldig Sed R005 på R_BUC_02`() {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R005.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE),
            "SE",
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LocalDate.of(1952, 3, 9),
            SaksInfoSamlet(saktypeFraSed = ALDER, sakInformasjonFraPesys = SakInformasjon(sakId = "12345", sakType = ALDER, sakStatus = LOPENDE)),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.R005)
        )
        assertEquals(PENSJON, opprettJournalpostRequestCapturingSlot.captured.tema)
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer SENDT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R005.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "SE"
        )
        identifisertPerson.personListe = listOf(identifisertPerson, identifisertPerson)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            SaksInfoSamlet(saktypeFraSed = ALDER, sakInformasjonFraPesys = SakInformasjon(sakId = "12345", sakType = ALDER, sakStatus = LOPENDE)),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.R005)
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer MOTTATT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE),
            geografiskTilknytning = "3811"
        )
        val dodPerson = identifisertPersonPDL(
            "22078945602",
            sedPersonRelasjon(),
            "NO",
            "3811",
            personNavn = "Dod Begravet",
        )

        identifisertPerson.personListe = listOf(identifisertPerson, dodPerson)

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            fdato,
            SaksInfoSamlet(saktypeFraSed = ALDER),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.R005)
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }


    @Test
    fun `Sendt gyldig Sed P2000`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2000)
        )

        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Sendt gyldig Sed P2000 med UK saa skal landkode konverteres til GB fordi Pesys kun godtar GB`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_med_UK.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse, SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2000)
        )

        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Sendt gyldig Sed P2200`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )
        assertEquals(UFORE_UTLANDSTILSNITT, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Sendt Sed i P_BUC_10`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P15000.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P15000)
        )

        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat gyldig Sed P2000`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2000)
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE)
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2000)
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat gyldig Sed P2100`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "NOR"
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = ALDER),
            false,
            identifisertePersoner = 1,
            navAnsattInfo = null,
            SED(type = SedType.P2100)
        )
        assertEquals(NFP_UTLAND_AALESUND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat gyldig Sed P2200`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE)
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2200)
        )
        assertEquals(UFORE_UTLAND, opprettJPVurdering.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJPVurdering.captured.behandlingstema)
    }

    @Test
    fun `Mottat Sed i P_BUC_10`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_10_P15000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            fnr = SLAPP_SKILPADDE,
            personRelasjon = sedPersonRelasjon(fnr = SLAPP_SKILPADDE)
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P15000)
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som SAKSEIER så skal SEDen journalføres maskinelt`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )
        val sakInformasjon = SakInformasjon("111111", GJENLEV, LOPENDE, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet( saktypeFraSed = GJENLEV, sakInformasjonFraPesys = sakInformasjon),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som DELTAKER så skal SEDen journalføres maskinelt på gjenlevnde`() {

        val avdodFnr = "02116921297"
        val hendelse = """
            {
              "id": 27403,
              "sedId": "P2100_e6b0d1bfede5443face4059e720e9d43_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "1033470",
              "avsenderId": "NO:NAVAT07",
              "avsenderNavn": "NAV ACCEPTANCE TEST 07",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVAT08",
              "mottakerNavn": "Vilniaus",
              "mottakerLand": "LI",
              "rinaDokumentId": "e6b0d1bfede5443face4059e720e9d43",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "$avdodFnr"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertGjenlevendePerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.GJENLEVENDE, GJENLEV, rinaDocumentId = RINADOK_ID),
            "Test Testesen",
            "",
            null
        )
        val saksInfo = SakInformasjon("111111", GJENLEV, LOPENDE, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertGjenlevendePerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = GJENLEV, sakInformasjonFraPesys = saksInfo),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 med et norsk fnr eller dnr med UFØREP og sakstatus er Avsluttet så skal SED journalføres med oppgave ikke ferdigstille`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )
        val sakInformasjon = SakInformasjon("111222", UFOREP, AVSLUTTET, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            SaksInfoSamlet(sakInformasjonFraPesys = sakInformasjon),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
        // TODO: legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

    }

    @Test
    fun `Gitt at saksbehandler har opprettet en p2100 med mangelfullt fnr eller dnr så skal det opprettes en journalføringsoppgave og settes til enhet 4303`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            "",
            sedPersonRelasjon(),
            "NO"
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 og bestemsak returnerer ALDER og UFOREP som sakstyper så skal det opprettes en journalføringsoppgave og enhet setttes til 4303 NAV Id og fordeling`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "NO"
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )

        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at vi mottar en P_BUC_02 med kjent aktørid Når det finnes kun en sakstype fra bestemsak`() {

        val avdodFnr = "02116921297"
        val hendelse = """
            {
              "id": 27403,
              "sedId": "P2100_e6b0d1bfede5443face4059e720e9d43_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "1033470",
              "avsenderId": "NO:NAVAT07",
              "avsenderNavn": "POLEN",
              "avsenderLand": "PL",
              "mottakerId": "NO:NAVAT08",
              "mottakerNavn": "Oslo",
              "mottakerLand": "NO",
              "rinaDokumentId": "e6b0d1bfede5443face4059e720e9d43",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "$avdodFnr"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertGjenlevendePerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(STERK_BUSK, Relasjon.GJENLEVENDE, BARNEP, rinaDocumentId = RINADOK_ID)
        )
        val saksInfo = SakInformasjon("111111", BARNEP, LOPENDE, "4862", false)

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertGjenlevendePerson,
            STERK_BUSK.getBirthDate(),
            SaksInfoSamlet(saktypeFraSed = BARNEP, sakInformasjonFraPesys = saksInfo),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = SED(type = SedType.P2100)
        )

        verify(atLeast = 1) {
            journalpostKlient.opprettJournalpost(any(), any(), any())
        }
    }

    @ParameterizedTest
    @CsvSource(
        "P2000, P_BUC_01, PENSJON",
        "P2100, P_BUC_02, PENSJON",
        "P2200, P_BUC_03, UFORETRYGD"
    )
    fun `gitt at det er en sed av type P2000, P2100 eller P2200 saa skal Tema settes til UFORE ELLER PENSJON`(sedtype: String, buc: String, tema: String) {
        val currentSed = SED(type = SedType.valueOf(sedtype))
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.valueOf(buc)
            every { sedType } returns SedType.valueOf(sedtype)
        }
        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2,null, currentSed)
        assertEquals(Tema.valueOf(tema), result)
    }

    @Test
    fun tester() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_01
            every { sedType } returns SedType.P4000
        }
        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, null, SED(type = SedType.P4000))

        assertEquals(Tema.valueOf(PENSJON.toString()), result)
    }

    @Test
    fun `Gitt en P12000 i P_BUC_08 med tema ufoere så skal tema bli uforep`() {
        val p12000 = P12000(type = SedType.P12000, pensjon = P12000Pensjon(pensjoninfo = listOf(Pensjoninfo(betalingsdetaljer = Betalingsdetaljer(pensjonstype = "02")))))
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_08
            every { sedType } returns SedType.P12000
        }

        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, null, p12000)

        assertEquals(Tema.valueOf(UFORETRYGD.toString()), result)
    }

    @Test
    fun `Gitt en P12000 i P_BUC_08 med tema gjenlevende så skal tema bli PEN`() {
        val p12000 = P12000(type = SedType.P12000, pensjon = P12000Pensjon(pensjoninfo = listOf(Pensjoninfo(betalingsdetaljer = Betalingsdetaljer(pensjonstype = "03")))))
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_08
            every { sedType } returns SedType.P12000
        }

        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, null, p12000)

        assertEquals(Tema.valueOf(PENSJON.toString()), result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype BARNEP så skal det settes teama PENSJON`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_02
            every { sedType } returns SedType.P8000
        }

        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(BARNEP), SED(type = SedType.P8000))
        assertEquals(PENSJON, result)
    }

    private fun saksInfoSamlet(sakType: SakType) = SaksInfoSamlet("321654", SakInformasjon("321654", sakType, LOPENDE), saktypeFraSed = sakType)

    @Test
    fun `gitt det er en P_BUC_02 med saktype UFOREP så skal det settes teama UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 1, saksInfoSamlet(UFOREP), SED(type = SedType.P8000))
        assertEquals(UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype GJENLEVENDE så skal det settes teama PENSJON`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(GJENLEV), SED(type = SedType.P8000))
        assertEquals(PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_01 med saktype ALDER så skal det settes teama PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(mockedSedhendelse,  SLAPP_SKILPADDE.getAge(), 2, saksInfoSamlet(ALDER), SED(type = SedType.P8000))
        val result2 = hentTemaService.hentTema(mockedSedhendelse, SLAPP_SKILPADDE.getAge(), 1, saksInfoSamlet(ALDER), SED(type = SedType.P8000))
        assertEquals(PENSJON, result)
        assertEquals(PENSJON, result2)
    }

    @Test
    fun `gitt det er en R_BUC_02 og sed er R004 og enhet er 4819 så skal det settes teama PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns R_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(mockedSedhendelse, SLAPP_SKILPADDE.getAge(), 1, saksInfoSamlet(ALDER), SED(type = SedType.P8000))
        assertEquals(PENSJON, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er UFOREP så skal det settes teama UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns R_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(
            mockedSedhendelse,
            LEALAUS_KAKE.getAge(),
            1,
            saksInfoSamlet(UFOREP),
            SED(type = SedType.P8000)
        )

        assertEquals(UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er ALDER så skal det settes teama PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns R_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(mockedSedhendelse, SLAPP_SKILPADDE.getAge(), 2, saksInfoSamlet(ALDER), SED(type = SedType.P8000))
        assertEquals(PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype IKKE er UFOREP så skal det settes tema PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_05
            every { sedType } returns SedType.P8000
        }
        val resultatGENRL = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(GENRL), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatGENRL)

        val resultatOMSORG = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(OMSORG), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatOMSORG)

        val resultatALDER = hentTemaService.hentTema(mockedSedhendelse, SLAPP_SKILPADDE.getAge(), 1, saksInfoSamlet(ALDER), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatALDER)

        val resultatGJENLEV = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(GJENLEV), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatGJENLEV)

        val resultatBARNEP = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(BARNEP), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatBARNEP)

        val resultatUFORE = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 1, saksInfoSamlet(UFOREP), SED(type = SedType.P8000))
        assertEquals(UFORETRYGD, resultatUFORE)
    }

    @Test
    fun `gitt det er en P_BUC_06 kravtypen er UFOREP så skal det settes tema UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_06
            every { sedType } returns SedType.P6000
        }

        val resultatGENRL = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(GENRL), SED(type = SedType.P6000))
        assertEquals(PENSJON, resultatGENRL)

        val resultatOMSORG = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(OMSORG), SED(type = SedType.P6000))
        assertEquals(PENSJON, resultatOMSORG)

        val resultatALDER = hentTemaService.hentTema(mockedSedhendelse, SLAPP_SKILPADDE.getAge(), 1, saksInfoSamlet(ALDER), SED(type = SedType.P6000))
        assertEquals(PENSJON, resultatALDER)

        val resultatGJENLEV = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(GJENLEV), SED(type = SedType.P6000))
        assertEquals(PENSJON, resultatGJENLEV)

        val resultatBARNEP = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(BARNEP), SED(type = SedType.P6000))
        assertEquals(PENSJON, resultatBARNEP)

        val resultatUFOREMedSakType = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 1, saksInfoSamlet(UFOREP), SED(type = SedType.P6000))
        assertEquals(UFORETRYGD, resultatUFOREMedSakType)

        val resultatUFORE = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 1, saksInfoSamlet(UFOREP),
            SED(type = SedType.P6000, pensjon = P6000Pensjon(vedtak = listOf(VedtakItem(type = "30")))
            )
        )
        assertEquals(UFORETRYGD, resultatUFORE)
        assertEquals(PENSJON, resultatGENRL)

        val resultatOMSORGP8000 = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(OMSORG), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatOMSORGP8000)

        val resultatALDERP8000 = hentTemaService.hentTema(mockedSedhendelse, SLAPP_SKILPADDE.getAge(), 1, saksInfoSamlet(ALDER), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatALDERP8000)

        val resultatGJENLEVP8000 = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(GJENLEV), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatGJENLEVP8000)

        val resultatBARNEPP8000 = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 2, saksInfoSamlet(BARNEP), SED(type = SedType.P8000))
        assertEquals(PENSJON, resultatBARNEPP8000)

        val resultatUFOREP8000 = hentTemaService.hentTema(mockedSedhendelse, LEALAUS_KAKE.getAge(), 1, saksInfoSamlet(UFOREP), SED(type = SedType.P8000))
        assertEquals(UFORETRYGD, resultatUFOREP8000)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype er UFOREP så skal det settes teama UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_05
            every { sedType } returns SedType.P8000
        }
        val result = hentTemaService.hentTema(
            mockedSedhendelse,
            LEALAUS_KAKE.getAge(),
            1,
            saksInfoSamlet(UFOREP),
            SED(type = SedType.P8000)
        )
        assertEquals(UFORETRYGD, result)
    }

}
