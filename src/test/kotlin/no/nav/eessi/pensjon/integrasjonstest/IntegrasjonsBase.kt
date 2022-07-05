package no.nav.eessi.pensjon.integrasjonstest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.clearAllMocks
import no.nav.eessi.pensjon.eux.model.buc.Document
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.listeners.SedSendtListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"

abstract class IntegrasjonsBase() {

    @Autowired
    lateinit var mottattListener: SedMottattListener

    @Autowired
    lateinit var sendtListener: SedSendtListener

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var consumerFactory: ConsumerFactory<String, String>

    @Autowired
    lateinit var producerFactory: ProducerFactory<String, String>

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()
    var mockServer: ClientAndServer

    private fun settOppUtitlityConsumer(topicName: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            "eessi-pensjon-group2",
            "false",
            embeddedKafka
        )
        consumerProperties["auto.offset.reset"] = "earliest"


        val container = KafkaMessageListenerContainer(consumerFactory, ContainerProperties(topicName))

        container.setupMessageListener(
            MessageListener<String, String> { record -> println("Konsumerer melding:  $record") }
        )
        return container
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

    fun initAndRunContainer(topic: String, oppgaveTopic: String): TestResult {
        println("*************************  INIT START *****************************")

        listAppender.start()
        deugLogger.addAppender(listAppender)

        val container = settOppUtitlityConsumer(topic)
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        val oppgaveContainer = settOppUtitlityConsumer(oppgaveTopic)
        oppgaveContainer.start()
        ContainerTestUtils.waitForAssignment(oppgaveContainer, embeddedKafka.partitionsPerTopic)

        println("*************************  INIT DONE *****************************")

        var template = KafkaTemplate(producerFactory).apply { defaultTopic = topic }
        return TestResult(template, container, oppgaveContainer)
    }

    protected fun opprettBucDocuments(file: String): List<Document> {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    fun randomPort() = ThreadLocalRandom.current().nextInt(1024, 10000)

    init {
        val port = randomPort()
        System.setProperty("mockServerport", "" + port)

        mockServer = ClientAndServer.startClientAndServer(port)

        mockServer.`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withQueryStringParameter("grant_type", "client_credentials")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/STStoken.json"))))
            )

        mockServer.`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath("/.well-known/openid-configuration")
        )
            .respond(
                HttpResponse.response()
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

    data class TestResult(
        val kafkaTemplate: KafkaTemplate<String, String>,
        val topic: KafkaMessageListenerContainer<String, String>,
        val oppgaveTopic: KafkaMessageListenerContainer<String, String>
    ) {

        fun sendMsgOnDefaultTopic(kafkaMsgFromPath : String){
            kafkaTemplate.sendDefault(javaClass.getResource(kafkaMsgFromPath).readText())
        }

        fun waitForlatch(sendtListener: SedSendtListener) = sendtListener.getLatch().await(10, TimeUnit.SECONDS)
        fun waitForlatch(mottattListener: SedMottattListener) = mottattListener.getLatch().await(10, TimeUnit.SECONDS)

    }
}

