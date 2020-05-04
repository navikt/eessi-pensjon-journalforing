package no.nav.eessi.pensjon.listeners

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
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

    @Mock
    lateinit var personidentifiseringService: PersonidentifiseringService

    @Mock
    lateinit var sedDokumentHelper: SedDokumentHelper

    lateinit var sedListener: SedListener

    @Mock
    lateinit var gyldigeHendelser: GyldigeHendelser


    @BeforeEach
    fun setup() {
        `when`(gyldigeHendelser.gyldigeUtgaendeHendelser()).thenReturn(listOf("P"))
        `when`(gyldigeHendelser.gyldigeInnkommendeHendelser()).thenReturn(listOf("P", "H_BUC_07"))

        sedListener = SedListener(jouralforingService, personidentifiseringService, sedDokumentHelper, gyldigeHendelser)
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))),cr,  acknowledgment)
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
        verify(jouralforingService, times(0)).journalfor(any(), any(), any(), any(), any())
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres så ack melding`() {
        `when`(gyldigeHendelser.gyldigeInnkommendeHendelser()).thenReturn(listOf("R_BUC_02"))

        val sedListener2 = SedListener(jouralforingService, personidentifiseringService, sedDokumentHelper, gyldigeHendelser)
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

//    @Test
//    fun `Sendt Sed med ugyldige verdier`(){
//        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/BAD_BUC_01.json")))
//        assertThrows<RuntimeException> {
//            sedListener.consumeSedSendt(hendelse,cr, acknowledgment)
//        }
//    }

    @Test
    fun `Mottat Sed med ugyldige verdier`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/BAD_BUC_01.json")))
        sedListener.consumeSedMottatt( hendelse, cr, acknowledgment)
        //denne inneholder da ikke guldig P eller H_BUC_07
        verify(acknowledgment).acknowledge()
    }

//    @Test
//    fun `Mottat Sed med ugyldige felter`(){
//        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/sed/BAD_BUC_02.json")))
//        assertThrows<java.lang.RuntimeException> {
//            sedListener.consumeSedMottatt(hendelse,cr, acknowledgment)
//        }
//    }

    @Test
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        verify(jouralforingService, times(0)).journalfor(any(), any(), any(), any(), any())
    }

    @Test
    fun `gitt en mottatt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        verify(jouralforingService, times(0)).journalfor(any(), any(), any(), any(), any())
    }

    @Test
    fun `gitt en mottatt hendelse inneholder H_BUC_07 skal behandlsens og resultat blie true`()  {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/H_BUC_07_H070.json")))
        val result = sedListener.gyldigMottattHendelse(hendelse)
        Assertions.assertEquals(true, result)

    }

//    @Test
//    fun `gitt en mottatt hendelse inneholder sektorKode P skal behandlsens og resultat blie true`()  {
//        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
//
//        val result = sedListener.gyldigMottattHendelse(hendelse)
//
//        Assertions.assertEquals(true, result)
//
//    }
//
//    @Test
//    fun `gitt en mottatt hendelse IKKE inneholder sektorKode P eller H_BUC_07 skal det ignoreres og resultat bli false`()  {
//        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
//        val result = sedListener.gyldigMottattHendelse(hendelse)
//        Assertions.assertEquals(false, result)
//    }

}
