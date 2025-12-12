package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.HentSakService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class, IntegrasjonsBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC]
)
internal class SedSendtIntegrationTest : IntegrasjonsBase() {

    @MockkBean(relaxed = true)
    private lateinit var gcpStorageService: GcpStorageService

    @Autowired
    private lateinit var hentSakService: HentSakService

    init {
        if(System.getProperty("mockServerport") == null){
            val port = System.getProperty("mockServerport")?.toInt() ?: PortFactory.findFreePort()
            mockServer = ClientAndServer(Configuration().apply { logLevel(Level.ERROR) }, port)
            System.setProperty("mockServerport", port.toString())
        }
    }

    @BeforeEach
    fun beforeEach() {
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { gcpStorageService.hentFraGjenny(any()) } returns null
    }


    @Disabled
    @Test
    fun `En P8000 med saksType, saksId og aktørId skal journalføres maskinelt`() {

        CustomMockServer()
            .mockBucResponse("/buc/148161" , "12312312312452345624355", "/fagmodul/alldocumentsids.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET, "/sed/P2100-PinNO-NAV.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer",HttpMethod.GET, "/pdf/pdfResponseMedTomtVedlegg.json")
            .mockHttpRequestFromFileWithBodyContains("hentPerson", HttpMethod.POST,"/pdl/hentPersonResponse.json")
            .mockHttpRequestFromFileWithBodyContains("hentIdenter", HttpMethod.POST,"/pdl/hentIdenterResponse.json")
            .mockHttpRequestFromJsonWithBodyContains("hentGeografiskTilknytning", HttpMethod.POST, emptyResponse())

            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medGjennyResponse()
            .mockPensjonsinformasjon()
            .medOppdaterDistribusjonsinfo()

        startJornalforingForSendt("/eux/hendelser/P_BUC_05_P8000.json")

        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P8000")
            .medtildeltEnhetsnr("4475")
    }
}
