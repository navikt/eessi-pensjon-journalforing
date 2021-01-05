package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.oppgaverouting.Norg2Klient
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

internal class JournalforingServiceTest {

    private val euxKlient = mockk<EuxKlient>()
    private val journalpostService = mockk<JournalpostService>(relaxUnitFun = true)
    private val fagmodulKlient = mockk<FagmodulKlient>()
    private val norg2Klient = mockk<Norg2Klient>()
    private val pdfService = mockk<PDFService>()
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxUnitFun = true)

    private val oppgaveRoutingService = OppgaveRoutingService(norg2Klient)

    private val journalforingService = JournalforingService(euxKlient,
            journalpostService,
            oppgaveRoutingService,
            pdfService,
            oppgaveHandler
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

        //MOCK RESPONSES

        //EUX - HENT SED DOKUMENT
        every { euxKlient.hentSedDokumenter(any(), any()) } returns "MOCK DOCUMENTS"

        //PDF -
        every { pdfService.parseJsonDocuments(any(), SedType.P2000) } returns Pair<String, List<EuxDokument>>("P2000 Supported Documents", emptyList())
        every { pdfService.parseJsonDocuments(any(), SedType.P2100) } returns Pair("P2100 Supported Documents", listOf(EuxDokument("usupportertVedlegg.xml", null, "bleh")))
        every { pdfService.parseJsonDocuments(any(), SedType.P2100) } returns Pair<String, List<EuxDokument>>("P2100 Krav om etterlattepensjon", emptyList())
        every { pdfService.parseJsonDocuments(any(), SedType.P2200) } returns Pair<String, List<EuxDokument>>("P2200 Supported Documents", emptyList())
        every { pdfService.parseJsonDocuments(any(), SedType.R004) } returns Pair<String, List<EuxDokument>>("R004 - Melding om utbetaling", emptyList())
        every { pdfService.parseJsonDocuments(any(), SedType.R005) } returns Pair<String, List<EuxDokument>>("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)", emptyList())
        every { pdfService.parseJsonDocuments(any(), SedType.P15000) } returns Pair<String, List<EuxDokument>>("P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)", emptyList())

        //JOURNALPOST OPPRETT JOURNALPOST
        every {
            journalpostService.opprettJournalpost(
                    rinaSakId = any(),
                    fnr = any(),
                    personNavn = any(),
                    bucType = any(),
                    sedType = any(),
                    sedHendelseType = any(),
                    journalfoerendeEnhet = any(),
                    arkivsaksnummer = any(),
                    dokumenter = any(),
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
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
                null,
                "SE",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "2536475861",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.R_BUC_02,
                    sedType = SedType.R004,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.OKONOMI_PENSJON,
                    arkivsaksnummer = null,
                    dokumenter = "R004 - Melding om utbetaling",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = YtelseType.ALDER
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
                null,
                "SE",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "2536475861",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.R_BUC_02,
                    sedType = SedType.R005,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                    arkivsaksnummer = null,
                    dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = YtelseType.UFOREP
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
                null,
                "SE",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET))
        identifisertPerson.personListe = listOf(identifisertPerson, identifisertPerson)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "2536475861",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.R_BUC_02,
                    sedType = SedType.R005,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                    arkivsaksnummer = null,
                    dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = YtelseType.UFOREP
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
                null,
                "",
                "3811",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET))
        val dodPerson = IdentifisertPerson(
                "22078945602",
                "Dod Begravet",
                null,
                "",
                "3811",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.AVDOD))

        identifisertPerson.personListe = listOf(identifisertPerson, dodPerson)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "2536475861",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.R_BUC_02,
                    sedType = SedType.R005,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                    arkivsaksnummer = null,
                    dokumenter = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = YtelseType.ALDER
            )
        }
    }


    @Test
    fun `Sendt gyldig Sed P2000`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)
        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147729",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_01,
                    sedType = SedType.P2000,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P2000 Supported Documents",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
            )
        }
    }

    @Test
    fun `Sendt gyldig Sed P2200`() {
        //FAGMODUL HENT ALLE DOKUMENTER
        val documents = mapJsonToAny(javaClass.getResource("/buc/P2200-NAV.json").readText(), typeRefs<List<Document>>())

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns documents

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "NOR",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = any(),
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_03,
                    sedType = SedType.P2200,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.UFORE_UTLANDSTILSNITT,
                    arkivsaksnummer = null,
                    dokumenter = "P2200 Supported Documents",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147729",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_10,
                    sedType = SedType.P15000,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147729",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_01,
                    sedType = SedType.P2000,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P2000 Supported Documents",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
            )
        }
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`() {
        val json = javaClass.getResource("/fagmodul/allDocumentsBuc01.json").readText()
        val allDocuments = mapJsonToAny(json, typeRefs<List<Document>>())

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns allDocuments

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = any(),
                    fnr = SLAPP_SKILPADDE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_01,
                    sedType = SedType.P2000,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P2000 Supported Documents",
                    avsenderLand = "NO",
                    avsenderNavn = any(),
                    ytelseType = any()
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
                null,
                "NOR",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147730",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.NFP_UTLAND_AALESUND,
                    arkivsaksnummer = null,
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "NO",
                    avsenderNavn = "NAVT003",
                    ytelseType = YtelseType.ALDER
            )
        }
    }

    @Test
    fun `Mottat gyldig Sed P2200`() {
        val json = javaClass.getResource("/buc/P2200-NAV.json").readText()
        val allDocuments = mapJsonToAny(json, typeRefs<List<Document>>())

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns allDocuments

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(SLAPP_SKILPADDE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = any(),
                    fnr = SLAPP_SKILPADDE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_03,
                    sedType = SedType.P2200,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.UFORE_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P2200 Supported Documents",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147729",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_10,
                    sedType = SedType.P15000,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)",
                    avsenderLand = any(),
                    avsenderNavn = any(),
                    ytelseType = any()
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )
        val sakInformasjon = SakInformasjon("111111", YtelseType.GJENLEV, SakStatus.LOPENDE, "4303", false)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.GJENLEV, 0, sakInformasjon)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147730",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
                    arkivsaksnummer = "111111",
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "NO",
                    avsenderNavn = "NAVT003",
                    ytelseType = YtelseType.GJENLEV
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.GJENLEVENDE, YtelseType.GJENLEV)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.GJENLEV, SakStatus.LOPENDE, "4303", false)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertGjenlevendePerson, fdato, YtelseType.GJENLEV, 0, saksInfo)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "1033470",
                    fnr = identifisertGjenlevendePerson.personRelasjon.fnr,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.AUTOMATISK_JOURNALFORING,
                    arkivsaksnummer = "111111",
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "NO",
                    avsenderNavn = "NAV ACCEPTANCE TEST 07",
                    ytelseType = YtelseType.GJENLEV
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, YtelseType.GJENLEV)
        )
        val sakInformasjon = SakInformasjon("111222", YtelseType.UFOREP, SakStatus.AVSLUTTET, "4303", false)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, sakInformasjon)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147730",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                    arkivsaksnummer = null,
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "NO",
                    avsenderNavn = "NAVT003",
                    ytelseType = null
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
                null,
                "NO",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147730",
                    fnr = LEALAUS_KAKE,
                    personNavn = null,
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                    arkivsaksnummer = null,
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "NO",
                    avsenderNavn = "NAVT003",
                    ytelseType = null
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "147730",
                    fnr = LEALAUS_KAKE,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.SENDT,
                    journalfoerendeEnhet = Enhet.ID_OG_FORDELING,
                    arkivsaksnummer = null,
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "NO",
                    avsenderNavn = "NAVT003",
                    ytelseType = null
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
                null,
                "",
                "",
                personRelasjon = PersonRelasjon(STERK_BUSK, Relasjon.GJENLEVENDE, YtelseType.BARNEP)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.BARNEP, SakStatus.LOPENDE, "4862", false)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertGjenlevendePerson, fdato, YtelseType.BARNEP, 0, saksInfo)

        verify {
            journalpostService.opprettJournalpost(
                    rinaSakId = "1033470",
                    fnr = identifisertGjenlevendePerson.personRelasjon.fnr,
                    personNavn = "Test Testesen",
                    bucType = BucType.P_BUC_02,
                    sedType = SedType.P2100,
                    sedHendelseType = HendelseType.MOTTATT,
                    journalfoerendeEnhet = Enhet.PENSJON_UTLAND,
                    arkivsaksnummer = null,
                    dokumenter = "P2100 Krav om etterlattepensjon",
                    avsenderLand = "PL",
                    avsenderNavn = "POLEN",
                    ytelseType = YtelseType.BARNEP
            )
        }
    }

}
