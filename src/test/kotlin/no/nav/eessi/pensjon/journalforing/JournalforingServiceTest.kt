package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.buc.FdatoHelper
import no.nav.eessi.pensjon.buc.FnrHelper
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.services.person.PersonV3Service
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.*
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.fagmodul.Krav
import no.nav.eessi.pensjon.services.journalpost.*
import no.nav.eessi.pensjon.services.person.BrukerMock
import no.nav.eessi.pensjon.services.pesys.PenService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalforingServiceTest {

    @Mock
    private lateinit var euxService: EuxService

    @Mock
    private lateinit var journalpostService: JournalpostService

    @Mock
    private lateinit var aktoerregisterService: AktoerregisterService

    @Mock
    private lateinit var personV3Service: PersonV3Service

    @Mock
    private lateinit var fagmodulService: FagmodulService

    @Mock
    private lateinit var oppgaveRoutingService: OppgaveRoutingService

    @Mock
    private lateinit var diskresjonService: DiskresjonService

    @Mock
    private lateinit var pdfService: PDFService

    @Mock
    private lateinit var fnrHelper: FnrHelper

    @Mock
    private lateinit var fdatoHelper: FdatoHelper

    @Mock
    private lateinit var oppgaveHandler: OppgaveHandler

    @Mock
    private lateinit var penService: PenService

    private lateinit var journalforingService: JournalforingService

    @BeforeEach
    fun setup() {

        journalforingService = JournalforingService(euxService,
                journalpostService,
                aktoerregisterService,
                personV3Service,
                fagmodulService,
                oppgaveRoutingService,
                pdfService,
                diskresjonService,
                oppgaveHandler,
                penService,
                fnrHelper,
                fdatoHelper,
                "q2"
            )

        //MOCK RESPONSES

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(anyString())

        //EUX - HENT FODSELSDATO
        doReturn("1964-04-19")
                .`when`(euxService)
                .hentFodselsDatoFraSed(anyString(), anyString())

        //EUX - Fdatoservice (fin fdato)
        doReturn("1964-04-01")
                .`when`(fdatoHelper)
                .finnFDatoFraSeder(any())

        //EUX - FnrServide (fin pin)
        doReturn("01055012345")
                .`when`(fnrHelper)
                .getFodselsnrFraSeder(any())

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

        doReturn(null)
                .whenever(diskresjonService).hentDiskresjonskode(any())

        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn(JournalPostResponse("123", "null", null, false))
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
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01_F001.json"))), HendelseType.SENDT )
        verify(journalpostService, times(0)).opprettJournalpost(
                rinaSakId= anyOrNull(),
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
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fnrHelper, times(0)).getFodselsnrFraSeder(any())
        verify(fdatoHelper, times(0)).finnFDatoFraSeder(any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), eq(null))
    }


    @Test
    fun `Sendt gyldig Sed P2000`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))), HendelseType.SENDT )
        verify(personV3Service).hentPerson(eq("12078945602"))
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

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100.json"))), HendelseType.SENDT )
        verify(personV3Service).hentPerson(eq("12078945602"))
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

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json"))), HendelseType.SENDT )
        verify(fnrHelper, times(1)).getFodselsnrFraSeder(any())
        verify(personV3Service, times(1)).hentPerson(any())
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
         journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10_P2000.json"))), HendelseType.SENDT )

        verify(personV3Service).hentPerson(eq("12078945602"))
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
    fun `Sendt Sed med ugyldige verdier`(){
        assertThrows<MismatchedInputException> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json"))), HendelseType.SENDT)
        }
    }

    @Test
    fun `Sendt Sed med ugyldige felter`(){
        assertThrows<MissingKotlinParameterException> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json"))), HendelseType.SENDT)
        }
    }

    @Test
    fun `gitt en mottatt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01_F001.json"))), HendelseType.MOTTATT )
        verify(journalpostService, times(0)).opprettJournalpost(
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
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fagmodulService, times(0)).hentYtelseKravType(any(), any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), eq(null))
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12078945602"))

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

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000_ugyldigFNR.json"))), HendelseType.MOTTATT )
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

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100.json"))), HendelseType.MOTTATT )

        verify(personV3Service).hentPerson(eq("12078945602"))
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

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(1)).hentPerson(any())
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

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10_P2000.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12078945602"))
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

    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        journalforingService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", bucType = BucType.P_BUC_10, sedType = SedType.P2000, navBruker = "1207 8945602"))
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){
        journalforingService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", navBruker = "1207-8945602"))
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`(){
        journalforingService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", navBruker = "1207/8945602"))
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Mottat Sed med ugyldige verdier`(){
        assertThrows<MismatchedInputException> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json"))), HendelseType.MOTTATT)
        }
    }

    @Test
    fun `Mottat Sed med ugyldige felter`(){
        assertThrows<MissingKotlinParameterException> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json"))), HendelseType.MOTTATT)
        }
    }

    @Test
    fun `Gitt en tom fnr naar fnr valideres saa svar invalid`(){
        assertFalse(journalforingService.isFnrValid(null))
    }

    @Test
    fun `Gitt en ugyldig lengde fnr naar fnr valideres saa svar invalid`(){
        assertFalse(journalforingService.isFnrValid("1234"))
    }

    @Test
    fun `Gitt en gyldig lengde fnr naar fnr valideres saa svar valid`(){
        assertTrue(journalforingService.isFnrValid("12345678910"))
    }
}
