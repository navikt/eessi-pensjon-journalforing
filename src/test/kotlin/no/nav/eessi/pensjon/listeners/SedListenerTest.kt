package no.nav.eessi.pensjon.listeners

import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
internal class SedListenerTest {

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    @Mock
    private lateinit var cr: ConsumerRecord<String, String>

    @Mock
    private lateinit var jouralforingService: JournalforingService

    @Mock
    private lateinit var personidentifiseringService: PersonidentifiseringService

    @Mock
    private lateinit var sedDokumentHelper: SedDokumentHelper

    @Mock
    private lateinit var bestemSakService: BestemSakService

    private lateinit var sedListener: SedListener

    @BeforeEach
    fun setup() {
        sedListener = SedListener(jouralforingService, personidentifiseringService, sedDokumentHelper, bestemSakService, "test")
        sedListener.initMetrics()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))),cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres så saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres så ack melding`() {
        val sedListener2 = SedListener(jouralforingService, personidentifiseringService, sedDokumentHelper, bestemSakService, "test")
        sedListener2.initMetrics()
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))

        sedListener2.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }


    @Test
    fun `gitt en exception ved sedSendt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertThrows<RuntimeException> {
            sedListener.consumeSedSendt("Explode!",cr, acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }

    @Test
    fun `gitt en exception ved sedMottatt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertThrows<RuntimeException> {
            sedListener.consumeSedMottatt("Explode!",cr, acknowledgment)
        }
        verify(acknowledgment, times(0)).acknowledge()
    }

    @Test
    fun `Mottat og sendt Sed med ugyldige verdier kaster exception`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/BAD_BUC_01.json")))
        //denne inneholder da ikke guldig P eller H_BUC_07
        assertThrows<JournalforingException> {
            sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        }

        assertThrows<JournalforingException> {
            sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        }
    }

    @Test
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        verify(acknowledgment).acknowledge()
        verifyZeroInteractions(jouralforingService)
    }

    @Test
    fun `gitt en mottatt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(acknowledgment).acknowledge()
        verifyZeroInteractions(jouralforingService)
    }

}
