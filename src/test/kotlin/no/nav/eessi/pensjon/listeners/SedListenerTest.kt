package no.nav.eessi.pensjon.listeners

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.journalforing.JournalforingService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.exceptions.base.MockitoException
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertFailsWith

@RunWith(MockitoJUnitRunner::class)
class SedListenerTest {

    @Mock
    lateinit var acknowledgment: Acknowledgment

    @Mock
    lateinit var jouralforingService: JournalforingService

    lateinit var sedListener: SedListener

    @Before
    fun setup() {
        sedListener = SedListener(jouralforingService)
        doThrow(MockitoException("Boom!")).`when`(jouralforingService).journalfor(eq("Explode!"), any())
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så så ack melding`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))), acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en exception ved sedSendt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertFailsWith<RuntimeException> {
            sedListener.consumeSedSendt("Explode!", acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }

    @Test
    fun `gitt en exception ved sedMottatt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertFailsWith<RuntimeException> {
            sedListener.consumeSedMottatt("Explode!", acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }
}
