package no.nav.eessi.pensjon.journalforing.listeners

import com.nhaarman.mockito_kotlin.*
import no.nav.eessi.pensjon.journalforing.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingServiceTest
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.Dokument
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(MockitoJUnitRunner::class)
class SedListenerTest {

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
    lateinit var acknowledgment: Acknowledgment

    lateinit var jouralforingService: JournalforingService

    lateinit var sedListener: SedListener

    @Before
    fun setup() {
        jouralforingService = JournalforingService(euxService, journalpostService, oppgaveService, aktoerregisterService, personV3Service, fagmodulService, oppgaveRoutingService)
        sedListener = SedListener(jouralforingService)

        doReturn("1964-04-19").`when`(euxService).hentFodselsDato(anyString(), anyString())
        doReturn(SedDokumenterResponse(sed = Dokument("filnavn", MimeType.PDF, "ipsum lorem"), vedlegg = null)).`when`(euxService).hentSedDokumenter(anyString(), anyString())
        doReturn(JournalPostResponse("123", "M", "null")).`when`(journalpostService).opprettJournalpost(any(), any(), eq(false))
        doReturn(OppgaveRoutingModel.Enhet.UFORE_UTLAND).`when`(oppgaveRoutingService).route(any(), any(), eq(null), any() ,eq(null))
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så opprett journalføringsoppgave med tilhørende journalpost`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), acknowledgment)
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
        verify(acknowledgment, times(1)).acknowledge()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så opprett journalføringsoppgave med tilhørende journalpost`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), acknowledgment)
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(journalpostService, times(1)).opprettJournalpost(any(), any(), eq(false))
        verify(acknowledgment, times(1)).acknowledge()
    }

    @Test
    fun `gitt en sed som ikke tilhører pensjon når sedSendt hendelse konsumeres så bare ack melding`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01.json"))), acknowledgment)
        verify(oppgaveService, times(0)).opprettOppgave(any())
        verify(journalpostService, times(0)).opprettJournalpost(any(), any(), eq(false))
        verify(euxService, times(0)).hentSedDokumenter(anyString(), anyString())
        verify(aktoerregisterService, times(0)).hentGjeldendeAktoerIdForNorskIdent(any())
        verify(personV3Service, times(0)).hentPerson(any())
        verify(fagmodulService, times(0)).hentYtelseTypeForPBuc10(any(), any())
        verify(oppgaveRoutingService, times(0)).route(any(), any(), eq(null), any() ,eq(null))
        verify(acknowledgment, times(1)).acknowledge()
    }
}