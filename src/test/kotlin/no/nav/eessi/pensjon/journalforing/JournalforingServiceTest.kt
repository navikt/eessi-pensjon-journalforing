package no.nav.eessi.pensjon.journalforing

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.pesys.SakInformasjon
import no.nav.eessi.pensjon.klienter.pesys.SakStatus
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
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
    private lateinit var journalpostKlient: JournalpostKlient

    @Mock
    private lateinit var fagmodulKlient: FagmodulKlient

    @Mock
    private lateinit var oppgaveRoutingService: OppgaveRoutingService

    @Mock
    private lateinit var pdfService: PDFService

    @Mock
    private lateinit var oppgaveHandler: OppgaveHandler

    private lateinit var journalforingService: JournalforingService

    private lateinit var fdato: LocalDate

    @BeforeEach
    fun setup() {
        fdato = LocalDate.now()

        journalforingService = JournalforingService(euxKlient,
                journalpostKlient,
                oppgaveRoutingService,
                pdfService,
                oppgaveHandler
            )
        journalforingService.initMetrics()

        //MOCK RESPONSES

        //EUX - HENT FODSELSDATO
        doReturn("1964-04-19")
                .`when`(euxKlient)
                .hentFodselsDatoFraSed(anyString(), anyString())

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


        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn(OpprettJournalPostResponse("123", "null", null, false))
                .`when`(journalpostKlient)
                .opprettJournalpost(
                    rinaSakId = anyOrNull(),
                    fnr = anyOrNull(),
                    personNavn = anyOrNull(),
                    bucType = anyOrNull(),
                    sedType = anyOrNull(),
                    sedHendelseType = anyOrNull(),
                    eksternReferanseId = anyOrNull(),
                    kanal = anyOrNull(),
                    journalfoerendeEnhet = anyOrNull(),
                    arkivsaksnummer = anyOrNull(),
                    dokumenter = anyOrNull(),
                    forsokFerdigstill = anyOrNull(),
                    avsenderLand = anyOrNull(),
                    avsenderNavn = anyOrNull(),
                    ytelseType = anyOrNull()
                )

        //OPPGAVEROUTING ROUTE
        doReturn(OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_01 && arg.ytelseType == null })

        doReturn(OppgaveRoutingModel.Enhet.OKONOMI_PENSJON)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.R_BUC_02 && arg.sedType == SedType.R004 })

        doReturn(OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.R_BUC_02 && arg.sedType == SedType.R005 })

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 && arg.ytelseType == null })

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_03 && arg.ytelseType == null })

        doReturn(OppgaveRoutingModel.Enhet.UFORE_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_10 && arg.ytelseType == null })

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_10 && arg.ytelseType == null })

        doReturn(OppgaveRoutingModel.Enhet.DISKRESJONSKODE)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_05 && arg.ytelseType == null })
    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R004.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "SE",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.ALDER, 0, null)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("R_BUC_02"),
                sedType = eq(SedType.R004.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4819"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R004 - Melding om utbetaling"),
                forsokFerdigstill = eq(false),
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
                "",
                "SE",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, null)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("R_BUC_02"),
                sedType = eq(SedType.R005.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("0001"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"),
                forsokFerdigstill = eq(false),
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
                "",
                "SE",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET))
        identifisertPerson.personListe = listOf(identifisertPerson, identifisertPerson)

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.R_BUC_02 && arg.ytelseType == YtelseType.UFOREP})


        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, null)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("R_BUC_02"),
                sedType = eq(SedType.R005.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4303"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"),
                forsokFerdigstill = eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq(YtelseType.UFOREP)
        )
    }

    @Test
    fun `Sendt gyldig Sed P2000`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_01"),
                sedType = eq(SedType.P2000.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("0001"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                forsokFerdigstill = eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Sendt gyldig Sed P2200`(){
        //FAGMODUL HENT ALLE DOKUMENTER
        doReturn(String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2200-NAV.json"))))
                .`when`(fagmodulKlient)
                .hentAlleDokumenter(anyString())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr = eq("01055012345"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_03"),
                sedType = eq(SedType.P2200.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4303"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2200 Supported Documents"),
                forsokFerdigstill = eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_10"),
                sedType = eq(SedType.P2000.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4862"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                forsokFerdigstill = eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_01"),
                sedType = eq(SedType.P2000.name),
                sedHendelseType = eq("MOTTATT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("0001"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                forsokFerdigstill = eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`(){
        val allDocuments = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/allDocumentsBuc01.json")))
        doReturn(allDocuments).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyString(),
                fnr = eq("01055012345"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_01"),
                sedType = eq(SedType.P2000.name),
                sedHendelseType = eq("MOTTATT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("0001"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                forsokFerdigstill = eq(false),
                avsenderLand = eq("NO"),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2100`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 && arg.ytelseType == YtelseType.UFOREP })

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq("MOTTATT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4862"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(false),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(null)
        )
    }

    @Test
    fun `Mottat gyldig Sed P2200`(){
        val allDocuments = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2200-NAV.json")))
        doReturn(allDocuments).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr = eq("01055012345"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_03"),
                sedType = eq(SedType.P2200.name),
                sedHendelseType = eq("MOTTATT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4303"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2200 Supported Documents"),
                forsokFerdigstill = eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_10"),
                sedType = eq(SedType.P2000.name),
                sedHendelseType = eq("MOTTATT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4862"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2000 Supported Documents"),
                forsokFerdigstill = eq(false),
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
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.GJENLEV, SakStatus.LOPENDE, "4303", false)

        doReturn(OppgaveRoutingModel.Enhet.AUTOMATISK_JOURNALFORING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 })

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0,saksInfo)


        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("9999"),
                arkivsaksnummer = eq("111111"),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(true),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(null)
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
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.GJENLEVENDE, YtelseType.GJENLEV)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.GJENLEV, SakStatus.LOPENDE, "4303", false)

        doReturn(OppgaveRoutingModel.Enhet.AUTOMATISK_JOURNALFORING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 })

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertGjenlevendePerson, fdato, YtelseType.GJENLEV, 0, saksInfo )


        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("1033470"),
                fnr = eq(identifisertGjenlevendePerson.personRelasjon.fnr),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("9999"),
                arkivsaksnummer = eq("111111"),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(true),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAV ACCEPTANCE TEST 07"),
                ytelseType = eq(YtelseType.GJENLEV)
        )

        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

    }

    @Test
    fun `Gitt at saksbehandler har opprettet en p2100 med et norsk fnr eller dnr med UFØREP og sakstatus er Avsluttet så skal SEDen journalføres med oppgave ikke ferdigstille`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )
        val saksInfo = SakInformasjon("111222", YtelseType.UFOREP, SakStatus.AVSLUTTET, "4303", false)

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 })

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, YtelseType.UFOREP, 0, saksInfo)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4303"),
                arkivsaksnummer = eq("111222"),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(false),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(YtelseType.UFOREP)
        )

        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

    }

    @Test
    fun `Gitt at saksbehandler har opprettet en p2100 med mangelfullt fnr eller dnr så skal det opprettes en journalføringsoppgave og settes til enhet 4303`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson(
                "",
                null,
                "",
                "NO",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 })

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null )

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq(null),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4303"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(false),
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
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12078945602", Relasjon.FORSIKRET)
        )

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 })

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, fdato, null, 0, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr = eq("12078945602"),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq("SENDT"),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("4303"),
                arkivsaksnummer = eq(null),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(false),
                avsenderLand = eq("NO"),
                avsenderNavn = eq("NAVT003"),
                ytelseType = eq(null)
        )
    }

    @Test
    fun `Gitt at vi mottar en P_BUC_02 med kjent aktørid der det finnes kun en sakstype så skal SEDen automatisk journalføres`() {

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

        doReturn(OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 })


        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertGjenlevendePerson = IdentifisertPerson(
                "12078945602",
                "Test Testesen",
                "",
                "",
                "",
                personRelasjon = PersonRelasjon("12071245602", Relasjon.GJENLEVENDE, YtelseType.BARNEP)
        )
        val saksInfo = SakInformasjon("111111", YtelseType.BARNEP, SakStatus.LOPENDE, "4862", false)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertGjenlevendePerson, fdato, YtelseType.BARNEP, 0, saksInfo)


        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("1033470"),
                fnr = eq(identifisertGjenlevendePerson.personRelasjon.fnr),
                personNavn = eq("Test Testesen"),
                bucType = eq("P_BUC_02"),
                sedType = eq(SedType.P2100.name),
                sedHendelseType = eq(HendelseType.MOTTATT.name),
                eksternReferanseId = eq(null),
                kanal = eq("EESSI"),
                journalfoerendeEnhet = eq("0001"),
                arkivsaksnummer = eq("111111"),
                dokumenter = eq("P2100 Krav om etterlattepensjon"),
                forsokFerdigstill = eq(false),
                avsenderLand = eq("PL"),
                avsenderNavn = eq("POLEN"),
                ytelseType = eq(YtelseType.BARNEP)
        )

        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

    }

}
