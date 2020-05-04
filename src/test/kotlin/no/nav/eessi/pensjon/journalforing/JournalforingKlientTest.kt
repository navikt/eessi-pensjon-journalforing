package no.nav.eessi.pensjon.journalforing

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.fagmodul.Krav
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlientModel
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalforingKlientTest {

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

    @Mock
    private lateinit var bestemSakKlient: BestemSakKlient

    private lateinit var journalforingService: JournalforingService

    @BeforeEach
    fun setup() {

        journalforingService = JournalforingService(euxKlient,
                journalpostKlient,
                fagmodulKlient,
                oppgaveRoutingService,
                pdfService,
                oppgaveHandler,
                bestemSakKlient
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
                    fnr= anyOrNull(),
                    personNavn= anyOrNull(),
                    bucType= anyOrNull(),
                    sedType= anyOrNull(),
                    sedHendelseType= anyOrNull(),
                    eksternReferanseId= anyOrNull(),
                    kanal= anyOrNull(),
                    journalfoerendeEnhet= anyOrNull(),
                    arkivsaksnummer= anyOrNull(),
                    dokumenter= anyOrNull(),
                    forsokFerdigstill= anyOrNull(),
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

        //FAGMODUL HENT YTELSETYPE FOR P_BUC_10
        doReturn(Krav.YtelseType.UT.name)
                .`when`(fagmodulKlient)
                .hentYtelseKravType(anyString(), anyString())
    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R004.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                landkode = "SE"
            )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, "AP", 0)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("R_BUC_02"),
                sedType= eq(SedType.R004.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4819"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("R004 - Melding om utbetaling"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq("AP")
        )
    }

    @Test
    fun `Sendt gyldig Sed R005 på R_BUC_02`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                landkode = "SE"
        )

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, "UT", 0)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("2536475861"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("R_BUC_02"),
                sedType= eq(SedType.R005.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("0001"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = eq("UT")
        )
    }

    @Test
    fun `Sendt gyldig Sed P2000`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, null, 0)
        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_01"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("0001"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Sendt gyldig Sed P2100`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 && arg.ytelseType == "UT" })

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_02"),
                sedType= eq(SedType.P2100.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2100 Supported Documents"),
                forsokFerdigstill= eq(false),
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
        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr= eq("01055012345"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_03"),
                sedType= eq(SedType.P2200.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4303"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2200 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson, null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_10"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson,null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_01"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("0001"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false),
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

        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyString(),
                fnr= eq("01055012345"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_01"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("0001"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = eq("NO"),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2100`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(argWhere { arg -> arg.bucType == BucType.P_BUC_02 && arg.ytelseType == "UT" })

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson,null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147730"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_02"),
                sedType= eq(SedType.P2100.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2100 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2200`(){
        val allDocuments = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2200-NAV.json")))
        doReturn(allDocuments).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, null)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = anyOrNull(),
                fnr= eq("01055012345"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_03"),
                sedType= eq(SedType.P2200.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4303"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2200 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, null, 0)

        verify(journalpostKlient).opprettJournalpost(
                rinaSakId = eq("147729"),
                fnr= eq("12078945602"),
                personNavn= eq("Test Testesen"),
                bucType= eq("P_BUC_10"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false),
                avsenderLand = anyOrNull(),
                avsenderNavn = anyOrNull(),
                ytelseType = anyOrNull()
        )
    }
}
