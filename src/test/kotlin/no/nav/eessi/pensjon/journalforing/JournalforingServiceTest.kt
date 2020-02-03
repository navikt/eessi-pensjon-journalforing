package no.nav.eessi.pensjon.journalforing

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.fagmodul.Krav
import no.nav.eessi.pensjon.services.journalpost.*
import no.nav.eessi.pensjon.services.pesys.PenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.junit.jupiter.api.extension.ExtendWith
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
    private lateinit var euxService: EuxService

    @Mock
    private lateinit var journalpostService: JournalpostService

    @Mock
    private lateinit var fagmodulService: FagmodulService

    @Mock
    private lateinit var oppgaveRoutingService: OppgaveRoutingService

    @Mock
    private lateinit var pdfService: PDFService

    @Mock
    private lateinit var oppgaveHandler: OppgaveHandler

    @Mock
    private lateinit var penService: PenService

    private lateinit var journalforingService: JournalforingService

    @BeforeEach
    fun setup() {

        journalforingService = JournalforingService(euxService,
                journalpostService,
                fagmodulService,
                oppgaveRoutingService,
                pdfService,
                oppgaveHandler,
                penService
            )

        //MOCK RESPONSES

        //EUX - HENT FODSELSDATO
        doReturn("1964-04-19")
                .`when`(euxService)
                .hentFodselsDatoFraSed(anyString(), anyString())

        //EUX - HENT SED DOKUMENT
        doReturn("MOCK DOCUMENTS")
                .`when`(euxService)
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

        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn(OpprettJournalPostResponse("123", "null", null, false))
                .`when`(journalpostService)
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
                        avsenderNavn = anyOrNull()
                )

        //OPPGAVEROUTING ROUTE
        doReturn(OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_01), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_02), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(any(),
                    eq(BucType.P_BUC_03), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.UFORE_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_10), any())

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_10), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.DISKRESJONSKODE)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_05), eq(null))

        //FAGMODUL HENT YTELSETYPE FOR P_BUC_10
        doReturn(Krav.YtelseType.UT.name)
                .`when`(fagmodulService)
                .hentYtelseKravType(anyString(), anyString())
    }


    @Test
    fun `Sendt gyldig Sed P2000`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson)
        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Sendt gyldig Sed P2100`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Sendt gyldig Sed P2200`(){
        //FAGMODUL HENT ALLE DOKUMENTER
        doReturn(String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2200-NAV.json"))))
                .`when`(fagmodulService)
                .hentAlleDokumenter(anyString())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)
        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

         journalforingService.journalfor(sedHendelse, HendelseType.SENDT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`(){
        val allDocuments = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/allDocumentsBuc01.json")))
        doReturn(allDocuments).whenever(fagmodulService).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = eq("7477291"),
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2100`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Mottat gyldig Sed P2200`(){
        val allDocuments = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2200-NAV.json")))
        doReturn(allDocuments).whenever(fagmodulService).hentAlleDokumenter(any())

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
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
                avsenderNavn = anyOrNull()
        )
    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10_P2000.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("12078945602",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson)

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
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
                avsenderNavn = anyOrNull()
        )
    }
}
