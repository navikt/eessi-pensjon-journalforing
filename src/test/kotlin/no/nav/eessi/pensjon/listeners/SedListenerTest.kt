package no.nav.eessi.pensjon.listeners

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.journalforing.JournalforingService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.exceptions.base.MockitoException
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class SedListenerTest {

    @Mock
    lateinit var acknowledgment: Acknowledgment

    @Mock
    lateinit var cr: ConsumerRecord<String, String>

    @Mock
    lateinit var jouralforingService: JournalforingService

    lateinit var sedListener: SedListener

    @BeforeEach
    fun setup() {
        sedListener = SedListener(jouralforingService)
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))),cr,  acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så så ack melding`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))),cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en exception ved sedSendt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        doThrow(MockitoException("Boom!")).`when`(jouralforingService).journalfor(eq("Explode!"), any())

        assertThrows<RuntimeException> {
            sedListener.consumeSedSendt("Explode!",cr, acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }

    @Test
    fun `gitt en exception ved sedMottatt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        doThrow(MockitoException("Boom!")).`when`(jouralforingService).journalfor(eq("Explode!"), any())

        assertThrows<RuntimeException> {
            sedListener.consumeSedMottatt("Explode!",cr, acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }
}
