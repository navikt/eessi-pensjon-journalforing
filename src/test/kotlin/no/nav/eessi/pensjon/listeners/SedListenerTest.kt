package no.nav.eessi.pensjon.listeners

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import no.nav.eessi.pensjon.TestUtils
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

@ExtendWith(MockitoExtension::class)
class SedListenerTest {

    @Mock
    lateinit var acknowledgment: Acknowledgment

    @Mock
    lateinit var cr: ConsumerRecord<String, String>

    @Mock
    lateinit var jouralforingService: JournalforingService

    @Mock
    lateinit var personidentifiseringService: PersonidentifiseringService

    @Mock
    lateinit var sedDokumentHelper: SedDokumentHelper

    @Mock
    lateinit var bestemSakService: BestemSakService

    lateinit var sedListener: SedListener

    val gyldigeHendelser: GyldigeHendelser = GyldigeHendelser()
    val gyldigFunksjoner: GyldigFunksjoner = GyldigeFunksjonerToggleProd()

    @BeforeEach
    fun setup() {
        sedListener = SedListener(jouralforingService, personidentifiseringService, sedDokumentHelper, gyldigeHendelser, bestemSakService, gyldigFunksjoner,"test")
        sedListener.initMetrics()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(TestUtils.getResource("eux/hendelser/P_BUC_01_P2000.json"), cr,  acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedMottatt(TestUtils.getResource("eux/hendelser/P_BUC_01_P2000.json"),cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres så saa blir den ignorert`() {
        val hendelse = TestUtils.getResource("eux/hendelser/R_BUC_02_R005.json")
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres så ack melding`() {
        val sedListener2 = SedListener(jouralforingService, personidentifiseringService, sedDokumentHelper, gyldigeHendelser, bestemSakService, gyldigFunksjoner,"test")
        sedListener2.initMetrics()
        val hendelse = TestUtils.getResource("eux/hendelser/R_BUC_02_R005.json")

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
        val hendelse = TestUtils.getResource("eux/hendelser/BAD_BUC_01.json")
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
        val hendelse = TestUtils.getResource("eux/hendelser/FB_BUC_01_F001.json")
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        verify(jouralforingService, times(0)).journalfor(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `gitt en mottatt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = TestUtils.getResource("eux/hendelser/FB_BUC_01_F001.json")
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(jouralforingService, times(0)).journalfor(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `gitt en mottatt hendelse inneholder H_BUC_07 skal behandlsens og resultat blie true`()  {
        val hendelse = TestUtils.getResource("eux/hendelser/H_BUC_07_H070.json")
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(acknowledgment).acknowledge()
    }
}
