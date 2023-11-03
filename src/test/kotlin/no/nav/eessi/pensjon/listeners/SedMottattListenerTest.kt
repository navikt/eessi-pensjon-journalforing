package no.nav.eessi.pensjon.listeners

import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

internal class SedMottattListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val jouralforingService = mockk<JournalforingService>(relaxed = true)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val sedDokumentHelper = mockk<EuxService>(relaxed = true)
    private val bestemSakService = mockk<BestemSakService>(relaxed = true)

    private val sedListener = SedMottattListener(
        jouralforingService,
        personidentifiseringService,
        sedDokumentHelper,
        fagmodulService = mockk(relaxed = true),
        bestemSakService,
        "test",
    )
    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres, skal melding ackes`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en exception ved sedMottatt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertThrows<RuntimeException> {
            sedListener.consumeSedMottatt("Explode!", cr, acknowledgment)
        }
        verify { acknowledgment wasNot Called }
    }

    @Test
    fun `Mottat og sendt Sed med ugyldige verdier kaster exception`(){
        val hendelse = javaClass.getResource("/eux/hendelser/BAD_BUC_01.json")!!.readText()
        //Bucen inneholder ugyldig buctype og ugyldig sektorkode
        assertThrows<SedMottattRuntimeException> {
            sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)
        }
    }

    @Test
    fun `gitt en mottatt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedMottatt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
        verify { jouralforingService wasNot Called }
    }

}
