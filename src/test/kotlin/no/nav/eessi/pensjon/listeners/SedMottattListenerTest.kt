package no.nav.eessi.pensjon.listeners

import io.mockk.*
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.Sak
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

internal class SedMottattListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val jouralforingService = mockk<JournalforingService>(relaxed = true)
    private val fagmodulService = mockk<FagmodulService>(relaxed = true)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val euxService = mockk<EuxService>(relaxed = true)
    private val bestemSakService = mockk<BestemSakService>(relaxed = true)
    private val gcpStorageService = mockk<GcpStorageService>().apply {
        justRun { lagre(any(), any()) }
    }

    private val sedListener = SedMottattListener(
        jouralforingService,
        personidentifiseringService,
        euxService,
        fagmodulService,
        bestemSakService,
        gcpStorageService,
        "test"
    )
    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så ack melding`() {
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `Gitt en P_BUC_02 som er en gjenny buc saa skal den lagres i gjenny bucketen`() {
        val gjennysakIBucket = """
            {
              "sakId" : "123456",
              "sakType" : "OMSORG"
            }
        """.trimIndent()
        every { gcpStorageService.gjennyFinnes(any()) } returns true
        every { gcpStorageService.oppdaterGjennysak(any(), any(), any()) } returns "123546"
        every { gcpStorageService.hentFraGjenny(any()) } returns gjennysakIBucket
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json"))), cr, acknowledgment)

        verify(exactly = 1) { gcpStorageService.lagre(any(), any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `Gitt en mottatt H_BUC_07 `() {
        every { gcpStorageService.gjennyFinnes(any()) } returns true
        every { gcpStorageService.oppdaterGjennysak(any(), any(), any()) } returns "123546"
        every { gcpStorageService.hentFraGjenny(any()) } returns null
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/H_BUC_07_H070.json"))), cr, acknowledgment)

        verify(exactly = 0) { gcpStorageService.lagre(any(), any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
        verify(exactly = 1) { jouralforingService.journalfor(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Gitt en P_BUC_02 som ikke er P2100 saa skal den ikke lagres i gjenny bucketen`() {
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P10000.json"))), cr, acknowledgment)

        verify(exactly = 0) { gcpStorageService.lagre(any(), any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedMottatt hendelse konsumeres, skal melding ackes`() {
        every { gcpStorageService.gjennyFinnes(any()) } returns false
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
