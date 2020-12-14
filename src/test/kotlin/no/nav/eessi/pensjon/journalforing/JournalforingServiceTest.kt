package no.nav.eessi.pensjon.journalforing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalforingServiceTest {

    @Mock
    private lateinit var euxKlient: EuxKlient

    @Mock
    private lateinit var journalpostService: JournalpostService

    @Mock
    private lateinit var fagmodulKlient: FagmodulKlient


    @Mock
    private lateinit var norg2Klient: Norg2Klient

    @Mock
    private lateinit var pdfService: PDFService

    @Mock
    private lateinit var oppgaveHandler: OppgaveHandler

    private lateinit var journalforingService: JournalforingService

    private lateinit var fdato: LocalDate

    private lateinit var oppgaveRoutingService: OppgaveRoutingService

    @BeforeEach
    fun setup() {
        fdato = LocalDate.now()

        oppgaveRoutingService = OppgaveRoutingService(norg2Klient)

        journalforingService = JournalforingService(euxKlient,
                journalpostService,
                oppgaveRoutingService,
                pdfService,
                oppgaveHandler
        )
        journalforingService.initMetrics()

        //MOCK RESPONSES

        //EUX - HENT SED DOKUMENT
        doReturn("MOCK DOCUMENTS")
                .`when`(euxKlient)
                .hentSedDokumenter(anyString(), anyString())

        //PDF -
        doReturn(Pair<String, List<EuxDokument>>("P2000 Supported Documents", emptyList()))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.P2000))

        doReturn(Pair("P2100 Supported Documents", listOf(EuxDokument("usupportertVedlegg.xml", null, "bleh"))))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.P2100))

        doReturn(Pair<String, List<EuxDokument>>("P2100 Krav om etterlattepensjon", emptyList()))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.P2100))

        doReturn(Pair<String, List<EuxDokument>>("P2200 Supported Documents", emptyList()))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.P2200))

        doReturn(Pair<String, List<EuxDokument>>("R004 - Melding om utbetaling", emptyList()))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.R004))

        doReturn(Pair<String, List<EuxDokument>>("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)", emptyList()))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.R005))


        doReturn(Pair<String, List<EuxDokument>>("P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)", emptyList()))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq(SedType.P15000))


        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn(OpprettJournalPostResponse("123", "null", null, false))
                .`when`(journalpostService)
                .opprettJournalpost(
                        rinaSakId = anyOrNull(),
                        fnr = anyOrNull(),
                        personNavn = anyOrNull(),
                        bucType = anyOrNull(),
                        sedType = anyOrNull(),
                        sedHendelseType = anyOrNull(),
                        journalfoerendeEnhet = anyOrNull(),
                        arkivsaksnummer = anyOrNull(),
                        dokumenter = anyOrNull(),
                        avsenderLand = anyOrNull(),
                        avsenderNavn = anyOrNull(),
                        ytelseType = anyOrNull()
                )
    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R004.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "SE",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)
        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.R_BUC_02),
                sedType = eq(SedType.R004),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.OKONOMI_PENSJON),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R004 - Melding om utbetaling"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq(YtelseType.ALDER)
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.R_BUC_02),
                sedType = eq(SedType.R005),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.ID_OG_FORDELING),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq(YtelseType.UFOREP)
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET))
        identifisertPerson.personListe = listOf(identifisertPerson, identifisertPerson)


        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, null)
        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.R_BUC_02),
                sedType = eq(SedType.R005),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.ID_OG_FORDELING),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq(YtelseType.UFOREP)
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET))
        val dodPerson = IdentifisertPerson(
                "22078945602",
                "Dod Begravet",
                null,
                "",
                "3811",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.AVDOD))

        identifisertPerson.personListe = listOf(identifisertPerson, dodPerson)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.R_BUC_02),
                sedType = eq(SedType.R005),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.ID_OG_FORDELING),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq(YtelseType.ALDER)
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)
        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_01),
                sedType = eq(SedType.P2000),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.PENSJON_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Sendt gyldig Sed P2200`() {
        //FAGMODUL HENT ALLE DOKUMENTER
        val documents = mapJsonToAny(javaClass.getResource("/buc/P2200-NAV.json").readText(), typeRefs<List<Document>>())

        doReturn(documents)
                .`when`(fagmodulKlient)
                .hentAlleDokumenter(anyString())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "NOR",
                "",
                personRelasjon = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr = eq("01055012345"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_03),
                sedType = eq(SedType.P2200),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.UFORE_UTLANDSTILSNITT),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2200 Supported Documents"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_10),
                sedType = eq(SedType.P15000),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.PENSJON_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_01),
                sedType = eq(SedType.P2000),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.PENSJON_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`() {
        val json = javaClass.getResource("/fagmodul/allDocumentsBuc01.json").readText()
        val allDocuments = mapJsonToAny(json, typeRefs<List<Document>>())

        doReturn(allDocuments).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "",
                "",
                personRelasjon = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyString(),
                fnr = eq("01055012345"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_01),
                sedType = eq(SedType.P2000),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.PENSJON_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                avsenderLand = eq("NO"),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.NFP_UTLAND_AALESUND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(YtelseType.ALDER)
        )
    }

    @Test
    fun `Mottat gyldig Sed P2200`() {
        val json = javaClass.getResource("/buc/P2200-NAV.json").readText()
        val allDocuments = mapJsonToAny(json, typeRefs<List<Document>>())

        doReturn(allDocuments).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                null,
                "",
                "",
                personRelasjon = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr = eq("01055012345"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_03),
                sedType = eq(SedType.P2200),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.UFORE_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2200 Supported Documents"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_10),
                sedType = eq(SedType.P15000),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.PENSJON_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)"),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )
        val sakInformasjon = SakInformasjon("111111", YtelseType.GJENLEV, SakStatus.LOPENDE, "4303", false)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.GJENLEV, 0, sakInformasjon)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.AUTOMATISK_JOURNALFORING),
                arkivsaksnummer = eq("111111"),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(YtelseType.GJENLEV)
        )
        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.GJENLEVENDE, YtelseType.GJENLEV)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.GJENLEV, SakStatus.LOPENDE, "4303", false)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertGjenlevendePerson, fdato, YtelseType.GJENLEV, 0, saksInfo)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("1033470"),
                fnr = eq(identifisertGjenlevendePerson.personRelasjon.fnr),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.AUTOMATISK_JOURNALFORING),
                arkivsaksnummer = eq("111111"),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAV ACCEPTANCE TEST 07"),
                ytelseType = eq(YtelseType.GJENLEV)
        )

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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET, YtelseType.GJENLEV)
        )
        val sakInformasjon = SakInformasjon("111222", YtelseType.UFOREP, SakStatus.AVSLUTTET, "4303", false)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, sakInformasjon)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.ID_OG_FORDELING),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(null)
        )

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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq(null),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.ID_OG_FORDELING),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(null)
        )
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
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.SENDT),
                journalfoerendeEnhet = eq(Enhet.ID_OG_FORDELING),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(null)
        )
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
                personRelasjon = PersonRelasjon("12071245602", Relasjon.GJENLEVENDE, YtelseType.BARNEP)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.BARNEP, SakStatus.LOPENDE, "4862", false)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertGjenlevendePerson, fdato, YtelseType.BARNEP, 0, saksInfo)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("1033470"),
                fnr = eq(identifisertGjenlevendePerson.personRelasjon.fnr),
                personNavn = eq("Test Testesen"),
                bucType = eq(BucType.P_BUC_02),
                sedType = eq(SedType.P2100),
                sedHendelseType = eq(HendelseType.MOTTATT),
                journalfoerendeEnhet = eq(Enhet.PENSJON_UTLAND),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                avsenderLand = eq("PL"),
                avsenderNavn = eq("POLEN"),
                ytelseType = eq(YtelseType.BARNEP)
        )
    }

}
