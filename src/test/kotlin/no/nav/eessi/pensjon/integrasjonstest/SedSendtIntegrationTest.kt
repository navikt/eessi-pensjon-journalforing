package no.nav.eessi.pensjon.integrasjonstest

import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class, SedSendtIntegrationTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC]
)
internal class SedSendtIntegrationTest : IntegrasjonsBase() {

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

    @TestConfiguration
    class TestConfig {
        @Bean
        fun euxRestTemplate(): RestTemplate = IntegrasjonsTestConfig().mockedRestTemplate()

        @Bean
        fun euxKlient(): EuxKlientLib = EuxKlientLib(euxRestTemplate())
    }

    @Test
    fun `Når en sedSendt hendelse med en foreldre blir konsumert så skal den ikke opprette oppgave`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockHttpRequestWithResponseFromFile("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET,"/sed/P2000-NAV.json")

        meldingForSendtListener("/eux/hendelser/FB_BUC_01_F001.json")
    }

    @Test
    @Disabled
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medStatusAvbrutt()
            .medOppdaterDistribusjonsinfo()
            .mockHttpRequestWithResponseFromJson(
                "/buc/147666", HttpMethod.GET,
                Buc(
                    id = "147666",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/147666/sed/44cb68f89a2f4e748934fb4722721018",HttpMethod.GET,"/sed/P2000-NAV.json")
            .mockHttpRequestWithResponseFromFile("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer",HttpMethod.GET,"/pdf/pdfResponseMedUgyldigVedlegg.json")

        meldingForSendtListener( "/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")

        //verify route
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }

    @Disabled // Her opprettes det ikke oppgave lenger da person er ukjent og journalpost status settes til avbrutt
    @Test
    fun `Når en SED (P2200) hendelse blir konsumert skal det opprettes journalføringsoppgave`() {

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medStatusAvbrutt()
            .mockHttpRequestWithResponseFromJson(
                "/buc/148161",
                HttpMethod.GET,
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/44cb68f89a2f4e748934fb4722721018",HttpMethod.GET,"/sed/P2000-ugyldigFNR-NAV.json")
            .mockHttpRequestWithResponseFromFile( "/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer",HttpMethod.GET,"/pdf/pdfResonseMedP2000MedVedlegg.json" )

        meldingForSendtListener( "/eux/hendelser/P_BUC_03_P2200.json")

        //then route to 4303
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2200")
            .medtildeltEnhetsnr("4303")
    }

    @Test
    fun `En P8000 med saksType, saksId og aktørId skal journalføres maskinelt`() {

        CustomMockServer()
            .mockHttpRequestWithResponseFromJson(
                "/buc/148161",
                HttpMethod.GET,
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
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

    @Disabled
    @Test
    fun `Når en sed (X008) hendelse blir konsumert skal det opprettes journalføringsoppgave`() {

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medStatusAvbrutt()
            .mockHttpRequestWithResponseFromJson(
                "/buc/161558",
                HttpMethod.GET,
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/161558/sed/44cb68f89a2f4e748934fb4722721018",HttpMethod.GET,"/sed/P2000-ugyldigFNR-NAV.json")
            .mockHttpRequestWithResponseFromFile( "/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer",HttpMethod.GET,"/pdf/pdfResonseMedP2000MedVedlegg.json" )
            .mockHttpRequestWithResponseFromFile( "/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer",HttpMethod.GET,"/pdf/pdfResonseMedP2000MedVedlegg.json" )

        meldingForSendtListener( "/eux/hendelser/P_BUC_05_X008.json")

        //verify route
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("X008")
            .medtildeltEnhetsnr("4303")
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
