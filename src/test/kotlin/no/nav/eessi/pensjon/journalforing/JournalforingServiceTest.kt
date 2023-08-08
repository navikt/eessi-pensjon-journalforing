package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.klienter.journalpost.AvsenderMottaker
import no.nav.eessi.pensjon.klienter.journalpost.IdType
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"

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
        every { journalpostService.bestemBehandlingsTema(any(), any()) } returns Behandlingstema.BARNEP
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
                saktype = any(),
                institusjon = any()
            )
        } returns OpprettJournalPostResponse("123", "null", null, false)
    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json").readText()
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
            ALDER,
            0,
            null,
            SED(type = SedType.R004),
            antallIdentifisertePersoner = 1,
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R004,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.OKONOMI_PENSJON,
                arkivsaksnummer = null,
                dokumenter = "R004 - Melding om utbetaling",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = ALDER,
                institusjon = any()
            )
        }
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
            UFOREP,
            0,
            null,
            SED(type = SedType.R005),
            antallIdentifisertePersoner = 1
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = UFOREP,
                institusjon = any()
            )
        }
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
            UFOREP,
            0,
            null,
            SED(type = SedType.R005),
            antallIdentifisertePersoner = 1
            )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = UFOREP,
                institusjon = any()
            )
        }
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
            ALDER,
            0,
            null,
            SED(type = SedType.R005),
            antallIdentifisertePersoner = 1,
            )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "2536475861",
                fnr = LEALAUS_KAKE,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = ALDER,
                institusjon = any()
            )
        }
    }


    @Test
    fun `Sendt gyldig Sed P2000`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse, SENDT, identifisertPerson, LEALAUS_KAKE.getBirthDate(), null, 0, null,
            SED(type = SedType.P2000),
            antallIdentifisertePersoner = 1,
        )
        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_01,
                sedType = SedType.P2000,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                institusjon = any()
            )
        }
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
            sedHendelse, SENDT, identifisertPerson, LEALAUS_KAKE.getBirthDate(), null, 0, null,
            SED(type = SedType.P2000),
            antallIdentifisertePersoner = 1,
        )
        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_01,
                sedType = SedType.P2000,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                institusjon = AvsenderMottaker(
                    id = "UK:UKINST",
                    idType = IdType.UTL_ORG,
                    navn = "UK INST",
                    land = "GB"
                )
            )
        }
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
            null,
            0,
            null,
            SED(type = SedType.P2200),
            antallIdentifisertePersoner = 1,
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = any(),
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_03,
                sedType = SedType.P2200,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.UFORE_UTLANDSTILSNITT,
                arkivsaksnummer = null,
                dokumenter = "P2200 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                institusjon = any()
            )
        }
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
            null,
            0,
            null,
            SED(type = SedType.P15000),
            antallIdentifisertePersoner = 1,
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147729",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_10,
                sedType = SedType.P15000,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                institusjon = any()
            )
        }
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
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                institusjon = any()
            )
        }
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
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2000 Supported Documents",
                avsenderLand = "NO",
                avsenderNavn = any(),
                saktype = any(),
                any()
            )
        }
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
            ALDER,
            0,
            null, SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.NFP_UTLAND_AALESUND,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = ALDER,
                any()
            )
        }
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
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.UFORE_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2200 Supported Documents",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                any()
            )
        }
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
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)",
                avsenderLand = any(),
                avsenderNavn = any(),
                saktype = any(),
                any()
            )
        }
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som SAKSEIER så skal SEDen automatisk journalføres`() {

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
            GJENLEV,
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
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
                arkivsaksnummer = "111111",
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = GJENLEV,
                any()
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
            GJENLEV,
            0,
            saksInfo,
            SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "1033470",
                fnr = identifisertGjenlevendePerson.personRelasjon?.fnr,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
                arkivsaksnummer = "111111",
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAV ACCEPTANCE TEST 07",
                saktype = GJENLEV,
                any()
            )
        }
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
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = null,
                institusjon = any()
            )
        }
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
            sedHendelse, SENDT, identifisertPerson, fdato, null, 0, null, SED(type = SedType.P2100)
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = null,
                institusjon = any()
            )
        }
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
            sedHendelse, SENDT, identifisertPerson, fdato, null, 0, null, SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "147730",
                fnr = LEALAUS_KAKE,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = SENDT,
                journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "NO",
                avsenderNavn = "NAVT003",
                saktype = null,
                institusjon = any()
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
            BARNEP,
            0,
            saksInfo,
            SED(type = SedType.P2100),
        )

        verify {
            journalpostService.opprettJournalpost(
                rinaSakId = "1033470",
                fnr = identifisertGjenlevendePerson.personRelasjon?.fnr,
                bucType = P_BUC_02,
                sedType = SedType.P2100,
                sedHendelseType = MOTTATT,
                journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                arkivsaksnummer = null,
                dokumenter = "P2100 Krav om etterlattepensjon",
                avsenderLand = "PL",
                avsenderNavn = "POLEN",
                saktype = BARNEP,
                any()
            )
        }
    }

    fun identifisertPersonPDL(
        aktoerId: String = AKTOERID,
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPersonPDL =
        IdentifisertPersonPDL(aktoerId, landkode, geografiskTilknytning, personRelasjon, fnr, personNavn = personNavn)

    fun sedPersonRelasjon(fnr: Fodselsnummer? = LEALAUS_KAKE, relasjon: Relasjon = Relasjon.FORSIKRET, rinaDocumentId: String = RINADOK_ID) =
        SEDPersonRelasjon(fnr = fnr, relasjon = relasjon, rinaDocumentId = rinaDocumentId)


}
