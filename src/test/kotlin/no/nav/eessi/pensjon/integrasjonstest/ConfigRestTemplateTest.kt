package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.config.RestTemplateConfig
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.document.MimeType
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.journalforing.HentSakService
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.listeners.SedSendtListener
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.navansatt.NavansattKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakKlient
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayOutputStream

@SpringBootTest( classes = [EessiPensjonJournalforingTestApplication::class, RestTemplateConfig::class, ConfigRestTemplateTest.TestConfig::class, RestTemplateConfig::class])
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC],
    brokerProperties = [
        "log.dirs=build/embedded-kafka-logs",
        "offsets.topic.replication.factor=1"
    ]
)
@MockkBeans(
    MockkBean(name = "navansattRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "bestemSakOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class])
)

internal class ConfigRestTemplateTest {
    @MockkBean
    private lateinit var clientConfigurationProperties: ClientConfigurationProperties

    @Autowired
    private lateinit var sedSendtListener: SedSendtListener

//    @Autowired
//    private lateinit var euxRestTemplate: RestTemplate

    @Autowired
    private lateinit var euxKlientLab: EuxKlientLib

    @MockkBean
    private lateinit var personService: PersonService

    private lateinit var fagmodulService: FagmodulService

    @MockkBean
    private lateinit var bestemSakKlient: BestemSakKlient

    @MockkBean
    private lateinit var journalpostKlient: JournalpostKlient

    @MockkBean
    private lateinit var gcpStorageService: GcpStorageService

    @MockkBean(relaxed = true)
    private lateinit var navansattKlient: NavansattKlient

    @MockkBean(relaxed = true)
    private lateinit var etterlatteService: EtterlatteService

    @MockkBean(relaxed = true)
    private lateinit var hentSakService: HentSakService

    @MockkBean(relaxed = true)
    private lateinit var safClient: SafClient

    @MockkBean(relaxed = true)
    private lateinit var fagmodulKlient: FagmodulKlient

    val rinaSakId = "147666"
    val dokumentId = "b12e06dda2c7474b9998c7139c666666"
    lateinit var mockServer: ClientAndServer

    @BeforeEach
    fun setup() {
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns emptyList()
        fagmodulService = FagmodulService(fagmodulKlient)

        every { personService.harAdressebeskyttelse(any()) } returns false
        every { personService.sokPerson(any()) } returns setOf(
            IdentInformasjon(
                JournalforingTestBase.FNR_VOKSEN_UNDER_62,
                IdentGruppe.FOLKEREGISTERIDENT
            ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
        )
        every { personService.hentPerson(NorskIdent(JournalforingTestBase.FNR_VOKSEN_UNDER_62)) } returns
                JournalforingTestBase().createBrukerWith(
                    JournalforingTestBase.FNR_VOKSEN_UNDER_62,
                    aktorId = JournalforingTestBase.AKTOER_ID
                )
        every { personService.hentPerson(NorskIdent(JournalforingTestBase.FNR_VOKSEN_2)) } returns
                JournalforingTestBase().createBrukerWith(
                    JournalforingTestBase.FNR_VOKSEN_2,
                    aktorId = JournalforingTestBase.AKTOER_ID
                )
        every { bestemSakKlient.kallBestemSak(any()) } returns mockk(relaxed = true)
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null
        every { gcpStorageService.gjennyFinnes(any())} returns false
        every { hentSakService.hentSak("147666") } returns mockk(relaxed = true)
    }

    init {
        if(System.getProperty("mockServerport") == null){
            val port = System.getProperty("mockServerport")?.toInt() ?: PortFactory.findFreePort()
            mockServer = ClientAndServer(Configuration().apply { logLevel(Level.ERROR) }, port)
            System.setProperty("mockServerport", port.toString())
        }
    }

    @TestConfiguration
    class TestConfig {

        @Bean
        @Primary
        fun euxKlientLib(): EuxKlientLib = EuxKlientLib(mockedRestTemplate())

        @Bean
        fun etterlatteRestTemplate(): RestTemplate = mockedRestTemplate()

        @Bean
        fun fagmodulOidcRestTemplate(): RestTemplate = mockedRestTemplate()

        @Bean
        fun journalpostOidcRestTemplate(): RestTemplate = mockedRestTemplate()

        @Bean
        @Primary
        fun hentSakService(): HentSakService = mockk(relaxed = true)

        fun mockedRestTemplate(): RestTemplate {
            val port = System.getProperty("mockServerport")
            return RestTemplateBuilder()
                .rootUri("http://localhost:${port}")
                .build()
        }

        @Bean
        @Primary
        fun euxOAuthRestTemplate(): RestTemplate {
            return RestTemplateBuilder().build().apply {
            }
        }
    }
    /**
     * Jackson har en begrensning på 20MB for dokumenter. Dette er en test for å verifisere at RestTemplateConfig
     * kan håndere filer som er større enn dette
     */
    @Test
    @Disabled
    fun `Konfigurasjon skal håndtere en pdf med dokumenter større enn 20b`() {
        val requestSlot = slot<OpprettJournalpostRequest>()

        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any(), null) } returns mockk(relaxed = true)

        sedSendtListener.consumeSedSendt(
            javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")!!.readText(),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        //sjekker at denne går gjennom og at det lages en oppgave
        assertEquals(PENSJON, requestSlot.captured.tema)
        assertEquals(ID_OG_FORDELING, requestSlot.captured.journalfoerendeEnhet)
    }

    @Test
    fun `skal kunne hente dokumenter over 20mb `() {
        val sedDokumentfiler = SedDokumentfiler(
            SedVedlegg("", MimeType.PDF, ""),
            listOf(SedVedlegg("", MimeType.PDF, createLargePdf(150000).toString()))
        )
        CustomMockServer().
        mockBucResponse("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer" ,sedDokumentfiler.toJson())

        val result = euxKlientLab.hentAlleDokumentfiler(rinaSakId, dokumentId)
    }

    fun CustomMockServer.mockBucResponse(endpoint: String, dokument: String) = apply {
        mockHttpRequestWithResponseFromJson(
            endpoint,
            HttpMethod.GET,
            dokument
        )
    }

    protected fun opprettBucDocuments(file: String): List<DocumentsItem> {
        val json = javaClass.getResource(file)!!.readText()
        return mapJsonToAny(json)
    }




        private fun withBucResponse() = withSuccess(
            Buc(
                id = "147666",
                participants = emptyList(),
                documents = mapJsonToAny(javaClass.getResource("/fagmodul/alldocumentsids.json")!!.readText())
            ).toJson(), MediaType.APPLICATION_JSON
        )



    fun createLargePdf(numberOfPages: Int): PDDocument {
        val document = PDDocument()
        // Load from Spring classpath resource (works in fat jar + Docker)
        val fontResource = ClassPathResource("fonts/Roboto-Regular.ttf")
        val font = fontResource.inputStream.use { input ->
            PDType0Font.load(document, input, true) // true = embed
        }

        repeat(numberOfPages) { pageIndex ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { contentStream ->
                contentStream.beginText()
                contentStream.setFont(font, 12f)
                contentStream.newLineAtOffset(50f, 750f)
                contentStream.showText("The sky was dark and gloomy.${pageIndex + 1} of $numberOfPages")
                contentStream.endText()
            }
        }
        ByteArrayOutputStream().use { outputStream ->
            document.save(outputStream)
            val sizeInBytes = outputStream.size()
            println("PDF size in memory: $sizeInBytes bytes (${sizeInBytes / 1024 / 1024} MB)")
        }
        return document
    }
}
