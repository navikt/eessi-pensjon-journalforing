package no.nav.eessi.pensjon.integrasjonstest

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.VurderBrukerInfo
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class, SedSendtIntegrationTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC, OPPDATER_OPPGAVE_TOPIC]
)
internal class SedSendtIntegrationTest : IntegrasjonsBase() {

    @Autowired
    private lateinit var gcpStorageService: GcpStorageService

    init {
        if (System.getProperty("mockServerport") == null) {
            mockServer = ClientAndServer(Configuration().apply {
                logLevel(Level.ERROR)
            }, PortFactory.findFreePort())
                .also {
                    System.setProperty("mockServerport", it.localPort.toString())
                }
        }
    }

    @BeforeEach
    fun myBeforeEach() {
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { gcpStorageService.journalFinnes(any()) } returns false
        justRun { gcpStorageService.lagreJournalpostDetaljer(any(), any(), any(), any(), any()) }
        every { gcpStorageService.hentFraJournal(any()) } returns null
        every { gcpStorageService.arkiverteSakerForRinaId(any(), any()) } returns emptyList()
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun euxRestTemplate(): RestTemplate = IntegrasjonsTestConfig().mockedRestTemplate()

        @Bean
        fun euxKlientLib(): EuxKlientLib = EuxKlientLib(euxRestTemplate())

        @Bean
        fun gcpStorageService(): GcpStorageService = mockk<GcpStorageService>()

        @Bean
        fun safClient(): SafClient = SafClient(IntegrasjonsTestConfig().mockedRestTemplate())

        @Bean
        fun journalforingUtenBruker(): VurderBrukerInfo = VurderBrukerInfo(safClient(), gcpStorageService(), mockk(), mockk())
        //TODO: Ingen kobling mellomg journalføringservice og denne.. gå gjennom de andre mock -beans også
    }

    @Test
    fun `Gitt en sedSendt hendelse med en foreldre blir konsumert så skal den ikke opprette oppgave`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockHttpRequestWithResponseFromFile("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET,"/sed/P2000-NAV.json")

        meldingForSendtListener("/eux/hendelser/FB_BUC_01_F001.json")
    }

    @Test
    fun `En P8000 med saksType, saksId og aktørId skal journalføres maskinelt`() {

        CustomMockServer()
            .mockHttpRequestWithResponseFromJson(
                "/buc/148161",
                HttpMethod.GET,
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET, "/sed/P2100-PinNO-NAV.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer",HttpMethod.GET, "/pdf/pdfResponseMedTomtVedlegg.json")
            .mockHttpRequestFromFileWithBodyContains("hentPerson", HttpMethod.POST,"/pdl/hentPersonResponse.json")
            .mockHttpRequestFromFileWithBodyContains("hentIdenter", HttpMethod.POST,"/pdl/hentIdenterResponse.json")
            .mockHttpRequestFromJsonWithBodyContains("hentGeografiskTilknytning", HttpMethod.POST, emptyResponse())

            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockPensjonsinformasjon()
            .medOppdaterDistribusjonsinfo()

        meldingForSendtListener("/eux/hendelser/P_BUC_05_P8000.json")

        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P8000")
            .medtildeltEnhetsnr("4475")
    }

    fun emptyResponse(): String {
        return """
            {
              "data" : {},
              "errors" : null
            }

        """.trimIndent()
    }
}
