package no.nav.eessi.pensjon.listeners

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifiserPersonHelper
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
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
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class SedListenerTest {

    @Mock
    lateinit var acknowledgment: Acknowledgment

    @Mock
    lateinit var cr: ConsumerRecord<String, String>

    @Mock
    lateinit var jouralforingService: JournalforingService

    @Mock
    lateinit var identifiserPersonHelper: IdentifiserPersonHelper

    lateinit var sedListener: SedListener

    @BeforeEach
    fun setup() {
        sedListener = SedListener(jouralforingService, identifiserPersonHelper)
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
        doThrow(MockitoException("Boom!")).`when`(jouralforingService).journalfor(any(), any(), any())

        assertThrows<RuntimeException> {
            sedListener.consumeSedSendt("Explode!",cr, acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }

    @Test
    fun `gitt en exception ved sedMottatt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        doThrow(MockitoException("Boom!")).`when`(jouralforingService).journalfor(any(), any(), any())

        assertThrows<RuntimeException> {
            sedListener.consumeSedMottatt("Explode!",cr, acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }

    @Test
    fun `Mottat Sed med ugyldige verdier`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_01.json")))

        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        assertThrows<MismatchedInputException> {
            sedListener.consumeSedMottatt(hendelse, )
        }
    }

    @Test
    fun `Mottat Sed med ugyldige felter`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json")))
        val sedHendelse = SedHendelseModel.fromJson(hendelse)

        val identifisertPerson = IdentifisertPerson("01055012345",
                "12078945602",
                LocalDate.of(89, 7, 12),
                "Test Testesen",
                null,
                null,
                null)

        assertThrows<MissingKotlinParameterException> {
            journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson)
        }
    }
}
