package no.nav.eessi.pensjon.journalforing.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.nhaarman.mockito_kotlin.*
import no.nav.eessi.pensjon.journalforing.models.BucType
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.Dokument
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.HentYtelseTypeResponse
import no.nav.eessi.pensjon.journalforing.services.fagmodul.Krav
import no.nav.eessi.pensjon.journalforing.services.journalpost.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OpprettOppgaveModel
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonMock
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
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

    lateinit var journalforingService: JournalforingService

    @Before
    fun setup() {

        journalforingService = JournalforingService(euxService, journalpostService, oppgaveService, aktoerregisterService, personV3Service, fagmodulService, oppgaveRoutingService)

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
        doReturn(SedDokumenterResponse(
                sed = Dokument("MockSedDocument_P2000", MimeType.PDF, "ipsum lorem"),
                vedlegg = listOf(
                        Dokument("Vedlegg1", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg2", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg3", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg4", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg5", MimeType.PDF, "ipsum lorem"))))
                .`when`(euxService)
                .hentSedDokumenter(eq("147729"), anyString())

        doReturn(SedDokumenterResponse(
                sed = Dokument("MockSedDocument_P2100", MimeType.PDF, "ipsum lorem"),
                vedlegg = listOf(
                        Dokument("Vedlegg1", null, "ipsum lorem"),
                        Dokument("Vedlegg2", null, "ipsum lorem"))))
                .`when`(euxService)
                .hentSedDokumenter(eq("147730"), anyString())

        doReturn(SedDokumenterResponse(
                sed = Dokument("MockSedDocument_P2200", MimeType.PDF, "ipsum lorem"),
                vedlegg = null))
                .`when`(euxService)
                .hentSedDokumenter(eq("148161"), anyString())


        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn(JournalPostResponse("123", "M", "null"))
                .`when`(journalpostService)
                .opprettJournalpost(any(), any(), eq(false))

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
        doReturn(HentYtelseTypeResponse("FNR", Krav( "DATE", Krav.YtelseType.UT)))
                .`when`(fagmodulService)
                .hentYtelseTypeForPBuc10(anyString(), anyString())
    }

    @Test
    fun `gitt en sendt sed som ikke tilhører pensjon så blir den ignorert`() {
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01.json"))), HendelseType.SENDT )
        verify(oppgaveService, times(0)).opprettOppgave(any())
        verify(journalpostService, times(0)).opprettJournalpost(any(), any(), eq(false))
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fagmodulService, times(0)).hentYtelseTypeForPBuc10(any(), any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), any(), any() ,eq(null))
    }

    @Test
    fun `Sendt gyldig Sed P2000`(){
        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val p2000JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_01_SENDT_journalpostRequest.json")))
        val p2000OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_01_opprettOppgaveModel.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), HendelseType.SENDT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.SENDT) , eq(false))
        assertEquals(p2000JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(p2000OppgaveModel, oppgaveCaptor.lastValue.asJson())
    }

    @Test
    fun `Sendt gyldig Sed P2100`(){
        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val p2100JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_02_SENDT_journalpostRequest.json")))
        val p2100OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_02_opprettOppgaveModel.json")))
        val p2100BehandleSed = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_02_BehandleSed.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02.json"))), HendelseType.SENDT )

        verify(personV3Service).hentPerson(eq("12378945602"))
        verify(euxService).hentFodselsDato(eq("147730"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.SENDT) , eq(false))
        assertEquals(p2100JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService, times(2)).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(p2100OppgaveModel, oppgaveCaptor.firstValue.asJson())
        assertEquals(p2100BehandleSed, oppgaveCaptor.lastValue.asJson())
    }

    @Test
    fun `Sendt gyldig Sed P2200`(){

        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val p2200JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_03_SENDT_journalpostRequest.json")))
        val p2200OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_03_opprettOppgaveModel.json")))


        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))), HendelseType.SENDT )
        verify(personV3Service, times(0)).hentPerson(any())
        verify(euxService).hentFodselsDato(eq("148161"), eq("f899bf659ff04d20bc8b978b186f1ecc"))

        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.SENDT), eq(false))
        assertEquals(p2200JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(p2200OppgaveModel, oppgaveCaptor.lastValue.asJson())
    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){

        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val P_BUC_10_JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_10_SENDT_journalpostRequest.json")))
        val P_BUC_10_OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_10_opprettOppgaveModel.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10.json"))), HendelseType.SENDT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))


        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.SENDT), eq(false))
        assertEquals(P_BUC_10_JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(P_BUC_10_OppgaveModel, oppgaveCaptor.lastValue.asJson())
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
        verify(oppgaveService, times(0)).opprettOppgave(any())
        verify(journalpostService, times(0)).opprettJournalpost(any(), any(), eq(false))
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fagmodulService, times(0)).hentYtelseTypeForPBuc10(any(), any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), any(), any() ,eq(null))
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){

        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val p2000JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_01_MOTTATT_journalpostRequest.json")))
        val p2000OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_01_opprettOppgaveModel.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.MOTTATT) , eq(false))
        assertEquals(p2000JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(p2000OppgaveModel, oppgaveCaptor.lastValue.asJson())
    }

    @Test
    fun `Mottat gyldig Sed P2100`(){

        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val p2100JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_02_MOTTATT_journalpostRequest.json")))
        val p2100OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_02_opprettOppgaveModel.json")))
        val p2100BehandleSed = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_02_BehandleSed.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02.json"))), HendelseType.MOTTATT )

        verify(personV3Service).hentPerson(eq("12378945602"))
        verify(euxService).hentFodselsDato(eq("147730"), eq("b12e06dda2c7474b9998c7139c841646"))

        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.MOTTATT) , eq(false))
        assertEquals(p2100JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService, times(2)).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(p2100OppgaveModel, oppgaveCaptor.firstValue.asJson())
        assertEquals(p2100BehandleSed, oppgaveCaptor.lastValue.asJson())
    }

    @Test
    fun `Mottat gyldig Sed P2200`(){

        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val p2200JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_03_MOTTATT_journalpostRequest.json")))
        val p2200OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_03_opprettOppgaveModel.json")))


        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(0)).hentPerson(any())
        verify(euxService).hentFodselsDato(eq("148161"), eq("f899bf659ff04d20bc8b978b186f1ecc"))

        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.MOTTATT), eq(false))
        assertEquals(p2200JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(p2200OppgaveModel, oppgaveCaptor.lastValue.asJson())
    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){

        val journalpostCaptor = argumentCaptor<JournalpostRequest>()
        val oppgaveCaptor = argumentCaptor<OpprettOppgaveModel>()

        val P_BUC_10_JournalpostRequest = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/P_BUC_10_MOTTATT_journalpostRequest.json")))
        val P_BUC_10_OppgaveModel = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/P_BUC_10_opprettOppgaveModel.json")))

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10.json"))), HendelseType.MOTTATT )
        verify(personV3Service).hentPerson(eq("12378945601"))
        verify(euxService).hentFodselsDato(eq("147729"), eq("b12e06dda2c7474b9998c7139c841646"))


        verify(journalpostService).opprettJournalpost(journalpostCaptor.capture(), eq(HendelseType.MOTTATT), eq(false))
        assertEquals(P_BUC_10_JournalpostRequest, journalpostCaptor.lastValue.toString())

        verify(oppgaveService).opprettOppgave(oppgaveCaptor.capture())
        assertEquals(P_BUC_10_OppgaveModel, oppgaveCaptor.lastValue.asJson())
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