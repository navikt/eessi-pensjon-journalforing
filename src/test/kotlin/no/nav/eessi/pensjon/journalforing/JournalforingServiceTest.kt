package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.*
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.fagmodul.HentPinOgYtelseTypeResponse
import no.nav.eessi.pensjon.services.fagmodul.Krav
import no.nav.eessi.pensjon.services.journalpost.*
import no.nav.eessi.pensjon.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.services.personv3.BrukerMock
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
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
    private lateinit var oppgaveService: OppgaveService

    @Mock
    private lateinit var aktoerregisterService: AktoerregisterService

    @Mock
    private lateinit var personV3Service: PersonV3Service

    @Mock
    private lateinit var fagmodulService: FagmodulService

    @Mock
    private lateinit var oppgaveRoutingService: OppgaveRoutingService

    @Mock
    private lateinit var begrensInnsynService: BegrensInnsynService

    @Mock
    private lateinit var pdfService: PDFService

    private lateinit var journalforingService: JournalforingService

    @BeforeEach
    fun setup() {

        journalforingService = JournalforingService(euxService,
                journalpostService,
                oppgaveService,
                aktoerregisterService,
                personV3Service,
                fagmodulService,
                oppgaveRoutingService,
                pdfService,
                begrensInnsynService)

        //MOCK RESPONSES

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(anyString())

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

        doReturn(null)
                .whenever(begrensInnsynService).begrensInnsyn(any())

        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn("123")
                .`when`(journalpostService)
                .opprettJournalpost(
                        rinaSakId = anyOrNull(),
                        navBruker= anyOrNull(),
                        personNavn= anyOrNull(),
                        avsenderId= anyOrNull(),
                        avsenderNavn= anyOrNull(),
                        mottakerId= anyOrNull(),
                        mottakerNavn= anyOrNull(),
                        bucType= anyOrNull(),
                        sedType= anyOrNull(),
                        sedHendelseType= anyOrNull(),
                        eksternReferanseId= anyOrNull(),
                        kanal= anyOrNull(),
                        journalfoerendeEnhet= anyOrNull(),
                        arkivsaksnummer= anyOrNull(),
                        arkivsaksystem= anyOrNull(),
                        dokumenter= anyOrNull(),
                        forsokFerdigstill= anyOrNull()
                )

        //OPPGAVEROUTING ROUTE
        doReturn(OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_01), any(), any(), anyString(), eq(null), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_02), any(), any(), anyString(), eq(null), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(eq("12345678901"), eq(BucType.P_BUC_03), eq("NOR"), any(), any(), eq(null), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.UFORE_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(anyString(), eq(BucType.P_BUC_10), anyString(), any(),  anyString(), eq(null), any())

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(anyString(), eq(BucType.P_BUC_10), anyString(), any(),  anyString(), eq(null), eq(null))

        doReturn(OppgaveRoutingModel.Enhet.DISKRESJONSKODE)
                .`when`(oppgaveRoutingService)
                .route(anyString(), eq(BucType.P_BUC_05), anyString(), any(),  anyString(), eq(Diskresjonskode.SPSF),  eq(null))

        //FAGMODUL HENT YTELSETYPE FOR P_BUC_10
        doReturn(HentPinOgYtelseTypeResponse("FNR", Krav( "DATE", Krav.YtelseType.UT)))
                .`when`(fagmodulService)
                .hentPinOgYtelseType(anyString(), anyString())

        //FAGMODUL HENT FNR FRA BUC
        doReturn("12345678901")
                .`when`(fagmodulService)
                .hentFnrFraBuc(anyString(), anyString())
    }

    @Test
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01_F001.json"))), HendelseType.SENDT )
        verify(oppgaveService, times(0)).opprettOppgave(any(), any(), any(), any(), any(), any(), any(), eq(HendelseType.SENDT))
        verify(journalpostService, times(0)).opprettJournalpost(
                rinaSakId= anyOrNull(),
                navBruker= anyOrNull(),
                personNavn= anyOrNull(),
                avsenderId= anyOrNull(),
                avsenderNavn= anyOrNull(),
                mottakerId= anyOrNull(),
                mottakerNavn= anyOrNull(),
                bucType= anyOrNull(),
                sedType= anyOrNull(),
                sedHendelseType= anyOrNull(),
                eksternReferanseId= anyOrNull(),
                kanal= anyOrNull(),
                journalfoerendeEnhet= anyOrNull(),
                arkivsaksnummer= anyOrNull(),
                arkivsaksystem= anyOrNull(),
                dokumenter= anyOrNull(),
                forsokFerdigstill= anyOrNull()
        )
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fagmodulService, times(0)).hentPinOgYtelseType(any(), any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), any(), any(), anyString() ,eq(null), eq(null))
    }


    @Test
    fun `Sendt gyldig Sed P2000`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))), HendelseType.SENDT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDatoFraSed(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_01"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("0001"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )
        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2000),
                eq("123"),
                eq("0001"),
                eq(null),
                eq("JOURNALFORING"),
                eq("147729"),
                eq(null),
                eq(HendelseType.SENDT)
        )
    }

    @Test
    fun `Sendt gyldig Sed P2100`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100.json"))), HendelseType.SENDT )

        verify(personV3Service).hentPerson(eq("12378945602"))
        verify(euxService).hentFodselsDatoFraSed(eq("147730"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12378945602"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_02"),
                sedType= eq(SedType.P2100.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2100 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2100),
                eq("123"),
                eq("4862"),
                eq(null),
                eq("JOURNALFORING"),
                eq("147730"),
                eq(null),
                eq(HendelseType.SENDT)
        )
        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2100),
                eq(null),
                eq("4862"),
                eq(null),
                eq("BEHANDLE_SED"),
                eq("147730"),
                eq("usupportertVedlegg.xml "),
                eq(HendelseType.SENDT)
        )
    }



    @Test
    fun `Sendt gyldig Sed P2200`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json"))), HendelseType.SENDT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService).hentFodselsDatoFraSed(eq("148161"), eq("f899bf659ff04d20bc8b978b186f1ecc"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12345678901"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT002"),
                mottakerNavn= eq("NAVT002"),
                bucType= eq("P_BUC_03"),
                sedType= eq(SedType.P2200.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4303"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2200 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2200),
                eq("123"),
                eq("4303"),
                eq(null),
                eq("JOURNALFORING"),
                eq("148161"),
                eq(null),
                eq(HendelseType.SENDT)
        )


    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){
         journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10_P2000.json"))), HendelseType.SENDT )

        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDatoFraSed(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_10"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )
        
        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2000),
                eq("123"),
                eq("4862"),
                eq(null),
                eq("JOURNALFORING"),
                eq("147729"),
                eq(null),
                eq(HendelseType.SENDT)
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
        verify(oppgaveService, times(0)).opprettOppgave(any(), any(), any(), any(), any(), any(), any(), eq(HendelseType.MOTTATT))
        verify(journalpostService, times(0)).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= anyOrNull(),
                personNavn= anyOrNull(),
                avsenderId= anyOrNull(),
                avsenderNavn= anyOrNull(),
                mottakerId= anyOrNull(),
                mottakerNavn= anyOrNull(),
                bucType= anyOrNull(),
                sedType= anyOrNull(),
                sedHendelseType= anyOrNull(),
                eksternReferanseId= anyOrNull(),
                kanal= anyOrNull(),
                journalfoerendeEnhet= anyOrNull(),
                arkivsaksnummer= anyOrNull(),
                arkivsaksystem= anyOrNull(),
                dokumenter= anyOrNull(),
                forsokFerdigstill= anyOrNull()
        )
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fagmodulService, times(0)).hentPinOgYtelseType(any(), any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), any(), any(), anyString() ,eq(null),eq(null))
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDatoFraSed(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_01"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("0001"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2000),
                eq("123"),
                eq("0001"),
                eq(null),
                eq("JOURNALFORING"),
                eq("147729"),
                eq(null),
                eq(HendelseType.MOTTATT)
        )
    }

    @Test
    fun `Mottat gyldig Sed P2100`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100.json"))), HendelseType.MOTTATT )

        verify(personV3Service).hentPerson(eq("12378945602"))
        verify(euxService).hentFodselsDatoFraSed(eq("147730"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12378945602"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_02"),
                sedType= eq(SedType.P2100.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2100 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2100),
                eq("123"),
                eq("4862"),
                eq(null),
                eq("JOURNALFORING"),
                eq("147730"),
                eq(null),
                eq(HendelseType.MOTTATT)
        )
        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2100),
                eq(null),
                eq("4862"),
                eq(null),
                eq("BEHANDLE_SED"),
                eq("147730"),
                eq("usupportertVedlegg.xml "),
                eq(HendelseType.MOTTATT)
        )
    }

    @Test
    fun `Mottat gyldig Sed P2200`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService).hentFodselsDatoFraSed(eq("148161"), eq("f899bf659ff04d20bc8b978b186f1ecc"))

        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12345678901"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT002"),
                mottakerNavn= eq("NAVT002"),
                bucType= eq("P_BUC_03"),
                sedType= eq(SedType.P2200.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4303"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2200 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2200),
                eq("123"),
                eq("4303"),
                eq(null),
                eq("JOURNALFORING"),
                eq("148161"),
                eq(null),
                eq(HendelseType.MOTTATT)
        )
    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10_P2000.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDatoFraSed(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))


        verify(journalpostService).opprettJournalpost(
                rinaSakId = anyOrNull(),
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_10"),
                sedType= eq(SedType.P2000.name),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq("4862"),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq(SedType.P2000),
                eq("123"),
                eq("4862"),
                eq(null),
                eq("JOURNALFORING"),
                eq("147729"),
                eq(null),
                eq(HendelseType.MOTTATT)
        )
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
        assertFalse(isFnrValid(null))
    }

    @Test
    fun `Gitt en ugyldig lengde fnr naar fnr valideres saa svar invalid`(){
        assertFalse(isFnrValid("1234"))
    }

    @Test
    fun `Gitt en gyldig lengde fnr naar fnr valideres saa svar valid`(){
        assertTrue(isFnrValid("12345678910"))
    }
}
