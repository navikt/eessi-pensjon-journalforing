package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.HentSakService
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class, SedMottattIntegrationTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC]
)
internal class SedMottattIntegrationTest : IntegrasjonsBase() {

    init {
        if (System.getProperty("mockServerport") == null) {
            mockServer = ClientAndServer(PortFactory.findFreePort())
                .also {
                    System.setProperty("mockServerport", it.localPort.toString())
                }
        }
    }

    @MockkBean(relaxed = true)
    lateinit var gcpStorageService: GcpStorageService

    private var hentSakService: HentSakService = mockk()

    @BeforeEach
    fun setUp() {
        listOf("147729", "147666", "7477291").forEach {
            every { hentSakService.hentSak(it) } returns mockk(relaxed = true)
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun euxRestTemplate(): RestTemplate = IntegrasjonsTestConfig().mockedRestTemplate()

        @Bean
        fun euxKlientLib(): EuxKlientLib = EuxKlientLib(euxRestTemplate())

        @Bean
        fun safClient(): SafClient = SafClient(IntegrasjonsTestConfig().mockedRestTemplate())
    }

    @Test
    fun `en mottatt H_BUC_07 skal journalfores`() {
        val bucId = "147729"
        val journalpostId = "429434388"
        val sedId1 = "44cb68f89a2f4e748934fb4722721018"
        val sedId2 = "9498fc46933548518712e4a1d5133113"

        CustomMockServer()
            .medJournalforing(false, journalpostId)
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockBucResponse("/buc/$bucId", bucId, "/fagmodul/alldocumentsids.json")
            .mockSedResponse("/buc/$bucId/sed/$sedId1", "/sed/H070-NAV.json")
            .mockFileResponse("/buc/$bucId/sed/$sedId1/filer", "/pdf/pdfResponseUtenVedlegg.json")
            .mockFileResponse("/buc/$bucId/sed/$sedId2/filer", "/pdf/pdfResponseUtenVedlegg.json")

        meldingForMottattListener("/eux/hendelser/H_BUC_07_H070.json")

        OppgaveMeldingVerification(journalpostId)
            .medHendelsetype("MOTTATT")
            .medSedtype("H070")
            .medtildeltEnhetsnr("4303")
    }
    @Test
    fun `Gitt en mottatt P2000 med en fdato som er lik med fdato i fnr så skal oppgaven routes til 4303`() {
        val bucId = "147729"
        val journalpostId = "429434378"
        val sedId1 = "44cb68f89a2f4e748934fb4722721018"
        val sedId2 = "b12e06dda2c7474b9998c7139c841646"

        // setup
        CustomMockServer()
            .medJournalforing(false, journalpostId)
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockBucResponse("/buc/$bucId", bucId, "/fagmodul/alldocumentsids.json")
            .mockSedResponse("/buc/$bucId/sed/$sedId1", "/sed/P2000-NAV.json")
            .mockFileResponse("/buc/$bucId/sed/$sedId2/filer", "/pdf/pdfResponseMedVedlegg.json")

        // send melding
        meldingForMottattListener("/eux/hendelser/P_BUC_01_P2000.json")

        // verifikasjon
        OppgaveMeldingVerification(journalpostId)
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }

    @Test
    fun `Sender Pensjon SED (P2000) med ugyldig FNR og forventer routing til 4303`() {
        // setup
        CustomMockServer()
            .medJournalforing(false, "429434388")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockBucResponse("/buc/7477291", "7477291", "/fagmodul/alldocuments_ugyldigFNR_ids.json")
            .mockSedResponse("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx", "/sed/P2000-ugyldigFNR-NAV.json")
            .mockFileResponse("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx/filer", "/pdf/pdfResponseUtenVedlegg.json")

        // send melding
        meldingForMottattListener("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")

        // verifisering
        OppgaveMeldingVerification("429434388")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }


    @Test
    fun `Sender Pensjon SED (P2000) med ugyldig vedlegg og skal routes til 9999`() {

        CustomMockServer()
            .medJournalforing()
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockBucResponse("/buc/147666", "147666", "/fagmodul/alldocumentsids.json")
            .mockSedResponse("/buc/147666/sed/44cb68f89a2f4e748934fb4722721018", "/sed/P2000-NAV.json")
            .mockFileResponse("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer", "/pdf/pdfResponseMedUgyldigVedlegg.json")

        meldingForMottattListener("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")

        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }

    private fun CustomMockServer.mockBucResponse(endpoint: String, bucId: String, documentsPath: String) = apply {
        mockHttpRequestWithResponseFromJson(
            endpoint,
            HttpMethod.GET,
            Buc(
                id = bucId,
                participants = emptyList(),
                documents = opprettBucDocuments(documentsPath)
            ).toJson()
        )
    }
    private fun CustomMockServer.mockSedResponse(endpoint: String, responseFile: String) = apply {
        mockHttpRequestWithResponseFromFile(endpoint, HttpMethod.GET, responseFile)
    }

    private fun CustomMockServer.mockFileResponse(endpoint: String, responseFile: String) = apply {
        mockHttpRequestWithResponseFromFile(endpoint, HttpMethod.GET, responseFile)
    }
}
