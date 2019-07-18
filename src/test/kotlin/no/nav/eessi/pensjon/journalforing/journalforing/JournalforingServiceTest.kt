package no.nav.eessi.pensjon.journalforing.journalforing

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
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
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
                sed = Dokument("filnavn", MimeType.PDF, "ipsum lorem"),
                vedlegg = listOf(
                        Dokument("Vedlegg1", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg2", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg3", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg4", MimeType.PDF, "ipsum lorem"),
                        Dokument("Vedlegg5", MimeType.PDF, "ipsum lorem"))))
                .`when`(euxService)
                .hentSedDokumenter(eq("147729"), anyString())

        doReturn(SedDokumenterResponse(
                sed = Dokument("filnavn", MimeType.PDF, "ipsum lorem"),
                vedlegg = listOf(
                        Dokument("Vedlegg1", null, "ipsum lorem"),
                        Dokument("Vedlegg2", null, "ipsum lorem"))))
                .`when`(euxService)
                .hentSedDokumenter(eq("147730"), anyString())

        doReturn(SedDokumenterResponse(
                sed = Dokument("filnavn", MimeType.PDF, "ipsum lorem"),
                vedlegg = null))
                .`when`(euxService)
                .hentSedDokumenter(eq("148161"), anyString())


        //JOURNALPOST OPPRETT JOURNALPOST
        doReturn(JournalPostResponse("123", "M", "null"))
                .`when`(journalpostService)
                .opprettJournalpost(any(), any(), eq(false))

        //OPPGAVEROUTING ROUTE
        doReturn(OppgaveRoutingModel.Enhet.UFORE_UTLAND)
                .`when`(oppgaveRoutingService)
                .route(any(), any(), any(), any() ,eq(null))

        doReturn(OppgaveRoutingModel.Enhet.ID_OG_FORDELING)
                .`when`(oppgaveRoutingService)
                .route(eq(null), any(), eq(null), any() ,eq(null))

        doReturn(OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
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
    fun `Sendt gyldig Sed P2000`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), HendelseType.SENDT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Sendt gyldig Sed P2100`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02.json"))), HendelseType.SENDT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(2)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Sendt gyldig Sed P2200`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))), HendelseType.SENDT )
        verify(personV3Service, times(0)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Sendt Sed i P_BUC_10`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10.json"))), HendelseType.SENDT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Sendt Sed med ugyldige verdier`(){
        assertFailsWith<Exception> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json"))), HendelseType.SENDT )
        }
    }

    @Test
    fun `Sendt Sed med ugyldige felter`(){
        assertFailsWith<Exception> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json"))), HendelseType.SENDT )
        }
    }

    @Test
    fun `Mottat gyldig Sed P2000`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Mottat gyldig Sed P2100`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(2)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Mottat gyldig Sed P2200`(){
        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(0)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Mottat Sed i P_BUC_10`(){

        journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_10.json"))), HendelseType.MOTTATT )
        verify(personV3Service, times(1)).hentPerson(any())
        verify(euxService, times(1)).hentFodselsDato(any(), any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
    }

    @Test
    fun `Mottat Sed med ugyldige verdier`(){
        assertFailsWith<Exception> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json"))), HendelseType.MOTTATT )
        }
    }

    @Test
    fun `Mottat Sed med ugyldige felter`(){
        assertFailsWith<Exception> {
            journalforingService.journalfor(String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json"))), HendelseType.MOTTATT )
        }
    }

}