package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
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
import no.nav.eessi.pensjon.services.personv3.PersonMock
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertFailsWith


@RunWith(MockitoJUnitRunner::class)
class JournalforingServiceTest {

    @Mock
    lateinit var euxService: EuxService

    @Mock
    lateinit var journalpostService: JournalpostService

    @Mock
    lateinit var oppgaveService: OppgaveService

    @Mock
    lateinit var aktoerregisterService: AktoerregisterService

    @Mock
    lateinit var personV3Service: PersonV3Service

    @Mock
    lateinit var fagmodulService: FagmodulService

    @Mock
    lateinit var oppgaveRoutingService: OppgaveRoutingService

    @Mock
    lateinit var pdfService: PDFService

    lateinit var journalforingService: JournalforingService

    @Before
    fun setup() {

        journalforingService = JournalforingService(euxService, journalpostService, oppgaveService, aktoerregisterService, personV3Service, fagmodulService, oppgaveRoutingService, pdfService)

        //MOCK RESPONSES

        //PERSONV3 - HENT PERSON
        doReturn(PersonMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(anyString())

        //EUX - HENT FODSELSDATO
        doReturn("1964-04-19")
                .`when`(euxService)
                .hentFodselsDato(anyString(), anyString())


        //EUX - HENT SED DOKUMENT
        doReturn("MOCK DOCUMENTS")
                .`when`(euxService)
                .hentSedDokumenter(anyString(), anyString())

        //PDF -
        doReturn(Pair<String, String?>("P2000 Supported Documents", null))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq("P2000_b12e06dda2c7474b9998c7139c841646_2"))

        doReturn(Pair<String, String?>("P2100 Supported Documents", "P2100 UnSupported Documents"))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq("P2100_b12e06dda2c7474b9998c7139c841646_2"))

        doReturn(Pair<String, String?>("P2200 Supported Documents", null))
                .`when`(pdfService)
                .parseJsonDocuments(any(), eq("P2200_f899bf659ff04d20bc8b978b186f1ecc_1"))

        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn("123")
                .`when`(journalpostService)
                .opprettJournalpost(
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
                .route(any(), eq(BucType.P_BUC_01), any(), any() ,eq(null))

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
                .`when`(oppgaveRoutingService)
                .route(any(), eq(BucType.P_BUC_02), any(), any() ,eq(null))

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(eq(null), eq(BucType.P_BUC_03), eq(null), any() ,eq(null))

        doReturn(OppgaveRoutingModel.Enhet.UFORE_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(anyString(), eq(BucType.P_BUC_10), anyString(), anyString(), any())

        //AKTOERREGISTER HENT AKTORID FOR NORSK IDENT
        doReturn("mockAktoerID")
                .`when`(aktoerregisterService)
                .hentGjeldendeAktoerIdForNorskIdent(anyString())

        //FAGMODUL HENT YTELSETYPE FOR P_BUC_10
        doReturn(HentPinOgYtelseTypeResponse("FNR", Krav( "DATE", Krav.YtelseType.UT)))
                .`when`(fagmodulService)
                .hentPinOgYtelseType(anyString(), anyString())
    }

    @Test
    fun `gitt en sendt sed som ikke tilhører pensjon så blir den ignorert`() {
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01.json"))), HendelseType.SENDT )
        verify(oppgaveService, times(0)).opprettOppgave(any(), any(), any(), any(), any(), any(), any())
        verify(journalpostService, times(0)).opprettJournalpost(
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
        verify(oppgaveRoutingService, times(0)).route(any(), any(), any(), any() ,eq(null))
    }


    @Test
    fun `Sendt gyldig Sed P2000`(){
        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val p2000JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_01_SENDT_journalpostRequest.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), HendelseType.SENDT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_01"),
                sedType= eq("P2000 - Krav om alderspensjon"),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )
        verify(oppgaveService).opprettOppgave(
                eq("P2000 - Krav om alderspensjon"),
                eq("123"),
                eq("0001"),
                eq("mockAktoerID"),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )
    }

    @Test
    fun `Sendt gyldig Sed P2100`(){
        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val p2100JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_02_SENDT_journalpostRequest.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02.json"))), HendelseType.SENDT )

        verify(personV3Service).hentPerson(eq("12378945602"))
        verify(euxService).hentFodselsDato(eq("147730"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq("12378945602"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_02"),
                sedType= eq("P2100 - Krav om gjenlevendepensjon"),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2100 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq("P2100 - Krav om gjenlevendepensjon"),
                eq("123"),
                eq("4862"),
                eq("mockAktoerID"),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )
        verify(oppgaveService).opprettOppgave(
                eq("P2100 - Krav om gjenlevendepensjon"),
                eq(null),
                eq("4862"),
                eq("mockAktoerID"),
                eq("BEHANDLE_SED"),
                eq("147730"),
                eq("P2100 UnSupported Documents")
        )
    }



    @Test
    fun `Sendt gyldig Sed P2200`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))), HendelseType.SENDT )
        verify(personV3Service, times(0)).hentPerson(any())
        verify(euxService).hentFodselsDato(eq("148161"), eq("f899bf659ff04d20bc8b978b186f1ecc"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq(null),
                personNavn= eq(null),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT002"),
                mottakerNavn= eq("NAVT002"),
                bucType= eq("P_BUC_03"),
                sedType= eq("P2200 - Krav om uførepensjon"),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2200 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq("P2200 - Krav om uførepensjon"),
                eq("123"),
                eq("4303"),
                eq(null),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )


    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10.json"))), HendelseType.SENDT )

        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_10"),
                sedType= eq("P2000 - Krav om alderspensjon"),
                sedHendelseType= eq("SENDT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )
        
        verify(oppgaveService).opprettOppgave(
                eq("P2000 - Krav om alderspensjon"),
                eq("123"),
                eq("4475"),
                eq("mockAktoerID"),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )
    }

    @Test
    fun `Sendt Sed med ugyldige verdier`(){
        assertFailsWith<MismatchedInputException> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json"))), HendelseType.SENDT )
        }
    }

    @Test
    fun `Sendt Sed med ugyldige felter`(){
        assertFailsWith<MissingKotlinParameterException> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json"))), HendelseType.SENDT )
        }
    }

    @Test
    fun `gitt en mottatt sed som ikke tilhører pensjon så blir den ignorert`() {
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01.json"))), HendelseType.SENDT )
        verify(oppgaveService, times(0)).opprettOppgave(any(), any(), any(), any(), any(), any(), any())
        verify(journalpostService, times(0)).opprettJournalpost(
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
        verify(oppgaveRoutingService, times(0)).route(any(), any(), any(), any() ,eq(null))
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_01"),
                sedType= eq("P2000 - Krav om alderspensjon"),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq("P2000 - Krav om alderspensjon"),
                eq("123"),
                eq("0001"),
                eq("mockAktoerID"),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )


    }

    @Test
    fun `Mottat gyldig Sed P2100`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02.json"))), HendelseType.MOTTATT )

        verify(personV3Service).hentPerson(eq("12378945602"))
        verify(euxService).hentFodselsDato(eq("147730"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq("12378945602"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_02"),
                sedType= eq("P2100 - Krav om gjenlevendepensjon"),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2100 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq("P2100 - Krav om gjenlevendepensjon"),
                eq("123"),
                eq("4862"),
                eq("mockAktoerID"),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )
        verify(oppgaveService).opprettOppgave(
                eq("P2100 - Krav om gjenlevendepensjon"),
                eq(null),
                eq("4862"),
                eq("mockAktoerID"),
                eq("BEHANDLE_SED"),
                eq("147730"),
                eq("P2100 UnSupported Documents")
        )

    }

    @Test
    fun `Mottat gyldig Sed P2200`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(0)).hentPerson(any())
        verify(euxService).hentFodselsDato(eq("148161"), eq("f899bf659ff04d20bc8b978b186f1ecc"))

        verify(journalpostService).opprettJournalpost(
                navBruker= eq(null),
                personNavn= eq(null),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT002"),
                mottakerNavn= eq("NAVT002"),
                bucType= eq("P_BUC_03"),
                sedType= eq("P2200 - Krav om uførepensjon"),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2200 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq("P2200 - Krav om uførepensjon"),
                eq("123"),
                eq("4303"),
                eq(null),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )

    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))


        verify(journalpostService).opprettJournalpost(
                navBruker= eq("12378945601"),
                personNavn= eq("Test Testesen"),
                avsenderId= eq("NO:NAVT003"),
                avsenderNavn= eq("NAVT003"),
                mottakerId= eq("NO:NAVT007"),
                mottakerNavn= eq("NAV Test 07"),
                bucType= eq("P_BUC_10"),
                sedType= eq("P2000 - Krav om alderspensjon"),
                sedHendelseType= eq("MOTTATT"),
                eksternReferanseId= eq(null),
                kanal= eq("EESSI"),
                journalfoerendeEnhet= eq(null),
                arkivsaksnummer= eq(null),
                arkivsaksystem= eq(null),
                dokumenter= eq("P2000 Supported Documents"),
                forsokFerdigstill= eq(false)
        )

        verify(oppgaveService).opprettOppgave(
                eq("P2000 - Krav om alderspensjon"),
                eq("123"),
                eq("4475"),
                eq("mockAktoerID"),
                eq("JOURNALFORING"),
                eq(null),
                eq(null)
        )
    }

    @Test
    fun `Mottat Sed med ugyldige verdier`(){
        assertFailsWith<MismatchedInputException> {
                journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json"))), HendelseType.MOTTATT )
        }
    }

    @Test
    fun `Mottat Sed med ugyldige felter`(){
        assertFailsWith<MissingKotlinParameterException> {
                journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json"))), HendelseType.MOTTATT )
        }
    }



}
