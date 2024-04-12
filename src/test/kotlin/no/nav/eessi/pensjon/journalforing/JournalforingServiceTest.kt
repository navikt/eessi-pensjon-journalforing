package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P2100
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
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
            SaksInfoSamlet(saktype = ALDER),
            SED(type = SedType.R004),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktype = ALDER),
            sed,
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SaksInfoSamlet(saktype = GJENLEV),
            sed = sed,
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "SE",
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            SaksInfoSamlet(saktype = UFOREP),
            SED(type = SedType.R005),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer SENDT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
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
            SaksInfoSamlet(saktype = UFOREP),
            SED(type = SedType.R005),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sedPersonRelasjon(),
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
            SaksInfoSamlet(saktype = ALDER),
            SED(type = SedType.R005),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2200),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            sed = SED(type = SedType.P15000),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SaksInfoSamlet(saktype = ALDER),
            SED(type = SedType.P2100), identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2200),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
        )
        assertEquals(UFORE_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat Sed i P_BUC_10`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P15000.json")))
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
            sed = SED(type = SedType.P15000),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SaksInfoSamlet( saktype = GJENLEV, sakInformasjon = sakInformasjon),
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SaksInfoSamlet(saktype = GJENLEV, sakInformasjon = saksInfo),
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SaksInfoSamlet(sakInformasjon = sakInformasjon),
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            sed = SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
            SaksInfoSamlet(saktype = BARNEP, sakInformasjon = saksInfo),
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = null,
            kravTypeFraSed = null
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
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.valueOf(buc)
            every { sedType } returns SedType.valueOf(sedtype)
        }
        val result = journalforingService.hentTema(mockedSedhendelse, null, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.valueOf(tema), result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype BARNEP så skal det settes teama UFORETRYGD`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_01
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, BARNEP, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype UFOREP så skal det settes teama UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_01
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, UFOREP, LEALAUS_KAKE, 1, null)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype GJENLEVENDE så skal det settes teama UFORETRYGD`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, GJENLEV, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en P_BUC_01 med saktype ALDER så skal det settes teama UFORETRYGD`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns P_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, null, LEALAUS_KAKE, 2, null)
        val result2 = journalforingService.hentTema(mockedSedhendelse, null, LEALAUS_KAKE, 1, null)
        assertEquals(Tema.UFORETRYGD, result)
        assertEquals(Tema.UFORETRYGD, result2)
    }

    @Test
    fun `gitt det er en R_BUC_02 og sed er R004 og enhet er 4819 så skal det settes teama PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.R_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, ALDER, LEALAUS_KAKE, 1, null)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er UFOREP så skal det settes teama UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.R_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, UFOREP, LEALAUS_KAKE, 1, null)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er ALDER så skal det settes teama PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.R_BUC_02
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, ALDER, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype IKKE er UFOREP så skal det settes teama PEN`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.P_BUC_05
            every { sedType } returns SedType.P8000
        }
        val resultatGENRL = journalforingService.hentTema(mockedSedhendelse, GENRL, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.UFORETRYGD, resultatGENRL)

        val resultatOMSORG = journalforingService.hentTema(mockedSedhendelse, OMSORG, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.UFORETRYGD, resultatOMSORG)

        val resultatALDER = journalforingService.hentTema(mockedSedhendelse, ALDER, fnr = SLAPP_SKILPADDE, 1, null)
        assertEquals(Tema.PENSJON, resultatALDER)

        val resultatGJENLEV = journalforingService.hentTema(mockedSedhendelse, GJENLEV, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.UFORETRYGD, resultatGJENLEV)

        val resultatBARNEP = journalforingService.hentTema(mockedSedhendelse, BARNEP, LEALAUS_KAKE, 2, null)
        assertEquals(Tema.UFORETRYGD, resultatBARNEP)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype er UFOREP så skal det settes teama UFO`() {
        val mockedSedhendelse = mockk<SedHendelse>(relaxUnitFun = true).apply {
            every { rinaSakId } returns RINADOK_ID
            every { bucType } returns BucType.P_BUC_05
            every { sedType } returns SedType.P8000
        }
        val result = journalforingService.hentTema(mockedSedhendelse, UFOREP, LEALAUS_KAKE, 1, null)
        assertEquals(Tema.UFORETRYGD, result)
    }

}
