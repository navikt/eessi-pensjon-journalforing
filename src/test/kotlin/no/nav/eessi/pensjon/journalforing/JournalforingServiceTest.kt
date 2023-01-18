package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

internal class JournalforingServiceTest {

    private val journalpostService = mockk<JournalpostService>(relaxUnitFun = true)
    private val pdfService = mockk<PDFService>()
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxUnitFun = true)
    private val kravHandeler = mockk<KravInitialiseringsHandler>()
    private val kravService = KravInitialiseringsService(kravHandeler)

    private val norg2Service = mockk<Norg2Service> {
        every { hentArbeidsfordelingEnhet(any()) } returns null
    }
    protected val automatiseringHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val automatiseringStatistikkPublisher = AutomatiseringStatistikkPublisher(automatiseringHandlerKafka)
    private val oppgaveRoutingService = OppgaveRoutingService(norg2Service)

    private val journalforingService = JournalforingService(
            journalpostService,
            oppgaveRoutingService,
            pdfService,
            oppgaveHandler,
            kravService,
            automatiseringStatistikkPublisher
    )

    private val fdato = LocalDate.now()

    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
        private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
        private val STERK_BUSK = Fodselsnummer.fra("12011577847")!!
    }

    @BeforeEach
    fun setup() {
        journalforingService.initMetrics()

        journalforingService.nameSpace = "test"

        //MOCK RESPONSES

        //PDF -
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2000) } returns Pair("P2000 Supported Documents", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2100) } returns Pair("P2100 Krav om etterlattepensjon", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2200) } returns Pair("P2200 Supported Documents", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.R004) } returns Pair("R004 - Melding om utbetaling", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.R005) } returns Pair("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P15000) } returns Pair("P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)", emptyList())

        //JOURNALPOST OPPRETT JOURNALPOST
        every {
            journalpostService.opprettJournalpost(
                rinaSakId = any(),
                fnr = any(),
                bucType = any(),
                sedType = any(),
                sedHendelseType = any(),
                journalfoerendeEnhet = any(),
                arkivsaksnummer = any(),
                dokumenter = any(),
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        } returns OpprettJournalPostResponse("123", "null", null, false)
    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json").readText()
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "SE",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            Saktype.ALDER,
            0,
            null,
            SED(type = SedType.R004),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R004,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.OKONOMI_PENSJON,
                arkivsaksnummer = null,
                dokumenter = "R004 - Melding om utbetaling",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = Saktype.ALDER
            )
        }
    }

    @Test
    fun `Sendt gyldig Sed R005 på R_BUC_02`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "SE",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            fdato,
            Saktype.UFOREP,
            0,
            null,
            SED(type = SedType.R005),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = Saktype.UFOREP
            )
        }
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer SENDT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "SE",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )
        identifisertPerson.personListe = listOf(identifisertPerson, identifisertPerson)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            fdato,
            Saktype.UFOREP,
            0,
            null,
            SED(type = SedType.R005),

            )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = Saktype.UFOREP
            )
        }
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer MOTTATT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "3811",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )
        val dodPerson = IdentifisertPerson(
            "22078945602",
            "Dod Begravet",
            "",
            "3811",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.AVDOD, rinaDocumentId =  "3123123")
        )

        identifisertPerson.personListe = listOf(identifisertPerson, dodPerson)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            fdato,
            Saktype.ALDER,
            0,
            null,
            SED(type = SedType.R005),

            )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = Saktype.ALDER
            )
        }
    }


    @Test
    fun `Sendt gyldig Sed P2000`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText()
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse, HendelseType.SENDT, identifisertPerson, LEALAUS_KAKE.getBirthDate(), null, 0, null,
            SED(type = SedType.P2000),
        )
        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_01,
                sedType = SedType.P2000,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Sendt gyldig Sed P2200`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "NOR",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            0,
            null,
            SED(type = SedType.P2200),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = any(),
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_03,
                sedType = SedType.P2200,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.UFORE_UTLANDSTILSNITT,
                arkivsaksnummer = null,
                dokumenter = "P2200 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Sendt Sed i P_BUC_10`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P15000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            0,
            null,
            SED(type = SedType.P15000),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_10,
                sedType = SedType.P15000,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Mottat gyldig Sed P2000`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            0,
            null,
            SED(type = SedType.P2000),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_01,
                sedType = SedType.P2000,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            null,
            0,
            null,
            SED(type = SedType.P2000),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = any(),
                fnr = SLAPP_SKILPADDE,
                bucType = P_BUC_01,
                sedType = SedType.P2000,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = "NO",
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Mottat gyldig Sed P2100`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "NOR",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            Saktype.ALDER,
            0,
            null, SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.NFP_UTLAND_AALESUND,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = Saktype.ALDER
            )
        }
    }

    @Test
    fun `Mottat gyldig Sed P2200`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            null,
            0,
            null,
            SED(type = SedType.P2200),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = any(),
                fnr = SLAPP_SKILPADDE,
                bucType = P_BUC_03,
                sedType = SedType.P2200,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.UFORE_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2200 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Mottat Sed i P_BUC_10`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P15000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            0,
            null,
            SED(type = SedType.P15000),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_10,
                sedType = SedType.P15000,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any()
            )
        }
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som SAKSEIER så skal SEDen automatisk journalføres`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )
        val sakInformasjon = SakInformasjon("111111", Saktype.GJENLEV, SakStatus.LOPENDE, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            Saktype.GJENLEV,
            0,
            sakInformasjon,
            SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
                arkivsaksnummer = "111111",
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = Saktype.GJENLEV
            )
            //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde
        }
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som DELTAKER så skal SEDen automatisk journalføres på Gjenlevnde`() {

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

        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertGjenlevendePerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.GJENLEVENDE, Saktype.GJENLEV, rinaDocumentId =  "3123123")
        )
        val saksInfo = SakInformasjon("111111", Saktype.GJENLEV, SakStatus.LOPENDE, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertGjenlevendePerson,
            LEALAUS_KAKE.getBirthDate(),
            Saktype.GJENLEV,
            0,
            saksInfo,
            SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "1033470",
                fnr = identifisertGjenlevendePerson.personRelasjon.fnr,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
                arkivsaksnummer = "111111",
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAV ACCEPTANCE TEST 07",
                saktype = Saktype.GJENLEV
            )
        }
        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 med et norsk fnr eller dnr med UFØREP og sakstatus er Avsluttet så skal SED journalføres med oppgave ikke ferdigstille`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, Saktype.GJENLEV, rinaDocumentId =  "3123123")
        )
        val sakInformasjon = SakInformasjon("111222", Saktype.UFOREP, SakStatus.AVSLUTTET, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            fdato,
            null,
            0,
            sakInformasjon,
            SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = null
            )
        }
        // TODO: legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

    }

    @Test
    fun `Gitt at saksbehandler har opprettet en p2100 med mangelfullt fnr eller dnr så skal det opprettes en journalføringsoppgave og settes til enhet 4303`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "",
            null,
            "NO",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null, SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = null
            )
        }
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 og bestemsak returnerer ALDER og UFOREP som sakstyper så skal det opprettes en journalføringsoppgave og enhet setttes til 4303 NAV Id og fordeling`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        journalforingService.journalfor(
            sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null, SED(type = SedType.P2100))

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = null
            )
        }
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

        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertGjenlevendePerson = IdentifisertPerson(
            "12078945602",
            "Test Testesen",
            "",
            "",
            personRelasjon = SEDPersonRelasjon(STERK_BUSK, Relasjon.GJENLEVENDE, Saktype.BARNEP, rinaDocumentId =  "3123123")
        )
        val saksInfo = SakInformasjon("111111", Saktype.BARNEP, SakStatus.LOPENDE, "4862", false)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertGjenlevendePerson,
            STERK_BUSK.getBirthDate(),
            Saktype.BARNEP,
            0,
            saksInfo,
            SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "1033470",
                fnr = identifisertGjenlevendePerson.personRelasjon.fnr,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = HendelseType.MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "PL",
                avsenderNavn = "POLEN",
                saktype = Saktype.BARNEP
            )
        }
    }

}
