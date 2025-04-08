package no.nav.eessi.pensjon.listeners

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

internal class SedSendtListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val jouralforingService = mockk<JournalforingService>(relaxed = true)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val euxService = mockk<EuxService>(relaxed = true)
    private val bestemSakService = mockk<BestemSakService>(relaxed = true)
    private val fagmodulService = mockk<FagmodulService>(relaxed = true)
    private val gcpStorageService = mockk<GcpStorageService>()

    private val sedListener = SedSendtListener(jouralforingService,
        personidentifiseringService,
        euxService,
        fagmodulService,
        bestemSakService,
        mockk(relaxed = true),
        gcpStorageService,
        "test")

    @BeforeEach
    fun setup() {
        every { gcpStorageService.gjennyFinnes(any()) } returns false
    }


    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "H_BUC_07, H070",
            "R_BUC_02, R005",
            "M_BUC_02, M02",
            "M_BUC_03a, M03a",
            "M_BUC_03b, M03bP"
        ]
    )
    fun `Hent ut alle som ligger i gyldigeHendelser listen`(bucType: String, sedType: String) {
        val enHendelse =
            """
                {
                  "id": 1869,
                  "sedId": "H070_9498fc46933548518712e4a1d5133113_2",
                  "sektorKode": "P",
                  "bucType": "$bucType",
                  "rinaSakId": "747729177",
                  "avsenderId": "NO:NAVT003",
                  "avsenderNavn": "NAVT003",
                  "avsenderLand": "NO",
                  "mottakerId": "NO:NAVT007",
                  "mottakerNavn": "NAV Test 07",
                  "mottakerLand": "NO",
                  "rinaDokumentId": "9498fc46933548518712e4a1d5133113",
                  "rinaDokumentVersjon": "2",
                  "sedType": "$sedType",
                  "navBruker": "09035225916"
                }
            """.trimIndent()

        every { gcpStorageService.gjennyFinnes(any()) } returns true
        every { gcpStorageService.oppdaterGjennysak(any(), any(), any()) } returns "747729177"
        every { gcpStorageService.hentFraGjenny(any()) } returns null

        sedListener.consumeSedSendt((enHendelse), cr, acknowledgment)

        verify(exactly = 0) { gcpStorageService.lagre(any(), any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
        verify(exactly = 1) { jouralforingService.journalfor(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }


    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedSendt hendelse konsumeres, skal melding ackes`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en exception ved sedSendt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertThrows<RuntimeException> {
            sedListener.consumeSedSendt("Explode!", cr, acknowledgment)
        }
        verify { acknowledgment wasNot Called }
    }

    @Test
    fun `Mottat og sendt Sed med ugyldige verdier kaster exception`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/BAD_BUC_01.json")))

        assertThrows<SedSendtRuntimeException> {
            sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        }
    }

    @Test
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
        verify { jouralforingService wasNot Called }
    }

}
