package no.nav.eessi.pensjon.integrasjonstest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.buc.Document
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.security.sts.STSService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

private lateinit var mockServer : ClientAndServer

abstract class MottattOgSendtIntegrationBase {

    @Autowired
    lateinit var sedListener: SedListener

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var personService: PersonService

    @Autowired
    lateinit var stsService: STSService

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @BeforeEach
    fun before() {
        println("************************* CLEANING UP AFTER CLASS*****************************")
    }

    @AfterEach
    fun after() {
        println("************************* CLEANING UP AFTER CLASS*****************************")
        clearAllMocks()
        embeddedKafka.kafkaServers.forEach { it.shutdown() }
        mockServer.stopAsync()
        mockServer.stop().also { print("mockServer -> HasStopped: ${mockServer.hasStopped()}") }
        listAppender.stop()
    }

    @TestConfiguration
    class TestConfig {
        @Value("\${" + EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS + "}")
        private lateinit var brokerAddresses: String

        @Bean
        fun personService(): PersonService {
            return mockk() {
                every { initMetrics() } just Runs
            }
        }

        @Bean
        fun kafkaProducerFactory(): ProducerFactory<String, String> {
            val configs = HashMap<String, Any>()
            configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
            configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            return DefaultKafkaProducerFactory(configs)
        }

        @Bean
        fun kafkaConsumerFactory(): ConsumerFactory<String, String> {
            val configs = HashMap<String, Any>()
            configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
            configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            configs[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
            configs[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            return DefaultKafkaConsumerFactory(configs)
        }

        @Bean
        @Primary
        fun kafkaTemplate(): KafkaTemplate<String, String> {
            return KafkaTemplate(kafkaProducerFactory())
        }
    }

    fun initAndRunContainer(): KafkaTemplate<String, String> {
        listAppender.start()
        deugLogger.addAppender(listAppender)
        return kafkaTemplate
    }

    protected fun opprettBucDocuments(file: String): List<Document> {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    fun randomPort() = ThreadLocalRandom.current().nextInt(1024, 10000)

    init {
        val port = 4946
        System.setProperty("mockServerport", "" + port).also {
            println("****************************** init med post: $it ********************************")
        }
        val hostWithPort = "http://localhost"

        mockServer = ClientAndServer.startClientAndServer(4946)

        mockServer.`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withQueryStringParameter("grant_type", "client_credentials"))
            .respond(HttpResponse.response()
                .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/STStoken.json"))))
            )

        mockServer.`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath("/.well-known/openid-configuration"))
            .respond(HttpResponse.response()
                .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withBody(
                    "{\n" +
                            "  \"issuer\": \"http://localhost:$port\",\n" +
                            "  \"token_endpoint\": \"http://localhost:$port/rest/v1/sts/token\",\n" +
                            "  \"exchange_token_endpoint\": \"http://localhost:$port/rest/v1/sts/token/exchange\",\n" +
                            "  \"jwks_uri\": \"http://localhost:$port/rest/v1/sts/jwks\",\n" +
                            "  \"subject_types_supported\": [\"public\"]\n" +
                            "}"
                )
            )
    }

    class CustomMockServer(
    ) {
        fun medOppdaterDistribusjonsinfo() = apply {
            // Mocker oppdaterDistribusjonsinfo
            MockServerClient("127.0.0.1", 4946).`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.PATCH.name)
                    .withPath("/journalpost/.*/oppdaterDistribusjonsinfo"))
                .respond(HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody("")
                )
        }

        fun mockBestemSak() = apply {

            MockServerClient("127.0.0.1", 4946).`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.POST.name)
                    .withPath("/")
            )
                .respond(
                    HttpResponse.response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(javaClass.getResource("/pen/bestemSakResponse.json").readText())
                        .withDelay(TimeUnit.SECONDS, 1)
                )
        }

        /**
         * mocker journalføringresponse
         * @param {Boolean} forsoekFerdigstill, gjør det mulig å benytte krav init
         * @param {String} journalpostId, erstatter default id
         * */
        fun medJournalforing(forsoekFerdigstill: Boolean = false, journalpostId: String = "429434378") = apply {
            MockServerClient("127.0.0.1", 4946).`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.POST.name)
                    .withPath("/journalpost")
            )
                .respond(
                    when (forsoekFerdigstill) {
                        true -> {
                            val reponse = withResponse("/journalpost/opprettJournalpostResponseTrue.json")
                            reponse.withBody(reponse.body.toString().replace("429434378", journalpostId))
                        }
                        false -> {
                            val reponse = withResponse("/journalpost/opprettJournalpostResponseFalse.json")
                            reponse.withBody(reponse.body.toString().replace("429434378", journalpostId))
                        }
                    }
                )
        }

        fun medNorg2Tjeneste() = apply {
            MockServerClient("127.0.0.1", 4946).`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.POST.name)
                    .withPath("/api/v1/arbeidsfordeling")
            )
                .respond(
                    withResponse("/norg2/norg2arbeidsfordelig4803result.json")
                )
        }

        fun medEuxGetRequest(bucPath: String, filePath: String) = apply {

            MockServerClient("127.0.0.1", 4946).`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.GET.name)
                    .withPath(bucPath)
            )
                .respond(
                    withResponse(filePath)
                )
        }

        fun medEuxGetRequestWithJson(bucPath: String, jsonAsString: String) = apply {

            MockServerClient("127.0.0.1", 4946).`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.GET.name)
                    .withPath(bucPath)
            )
                .respond(
                    withResponseAsJsonString(jsonAsString)
                )
        }

        private fun withResponse(filePath: String) = HttpResponse.response()
            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
            .withStatusCode(HttpStatusCode.OK_200.code())
            .withBody(javaClass.getResource(filePath).readText())

        private fun withResponseAsJsonString(jsonString: String) = HttpResponse.response()
            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
            .withStatusCode(HttpStatusCode.OK_200.code())
            .withBody(jsonString)
    }

    /**
     * Verifikasjons-tjeneste for oppgave
     */
    inner class OppgaveMeldingVerification(journalpostId: String) {
        val logsList: List<ILoggingEvent> = listAppender.list
        val meldingFraLog =
            logsList.find { message ->
                message.message.contains("Opprette oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding:") && message.message.contains(
                    "\"journalpostId\" : \"$journalpostId\""
                )
            }?.message
        fun medtildeltEnhetsnr(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"tildeltEnhetsnr\" : \"$melding\""))
        }

        fun medSedtype(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"sedType\" : \"$melding\""))
        }

        fun medHendelsetype(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"hendelseType\" : \"$melding\""))
        }
        fun medAktorId(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"aktoerId\" : \"$melding\""))
        }
    }

    fun mockPerson(fnr: String = "09035225916", aktorId: String = "1000101917358") : Person {
        val person = PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId(aktorId)
        )
        every { personService.hentPerson(NorskIdent(fnr)) }.answers { person }
        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        return person
    }
}

