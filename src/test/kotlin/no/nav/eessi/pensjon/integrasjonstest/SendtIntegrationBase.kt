package no.nav.eessi.pensjon.integrasjonstest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.eux.model.buc.Document
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toKotlinObject
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.listeners.SedListener
import org.junit.jupiter.api.AfterEach
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.*


private lateinit var mockServer : ClientAndServer

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1"

abstract class SendtIntegrationBase {

    @Autowired
    lateinit var sedListener: SedListener

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    protected val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon")  as Logger
    protected val listAppender = ListAppender<ILoggingEvent>()

    @AfterEach
    fun after() {
        shutdown()
    }

    private fun shutdown() {
        //mockServer.stop()
        embeddedKafka.kafkaServers.forEach { it.shutdown() }
    }

    //legger til hendlese på kafka her
    abstract fun produserSedHendelser(sedSendtProducerTemplate: KafkaTemplate<Int, String>)

    //override bruk denne for ekstra mockServere
    abstract fun moreMockServer(mockServer: ClientAndServer)

    //verifiser kall og asserts
    abstract fun verifiser()

    //legg til ekstra mockk o.l her
    open fun initExtraMock() {  }

    //** mock every må kjøres før denne
    fun initAndRunContainer() {

        // create and start a ListAppender
        listAppender.start()

        // add the appender to the logger
        // addAppender is outdated now
        deugLogger.addAppender(listAppender)

        initExtraMock()
//       moreMockServer(mockServer)

        // Vent til kafka er klar
        val container = settOppUtitlityConsumer(SED_SENDT_TOPIC)
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        // oppgave lytter kafka
        val oppgaveContainer = settOppUtitlityConsumer(OPPGAVE_TOPIC)
        oppgaveContainer.start()
        ContainerTestUtils.waitForAssignment(oppgaveContainer, embeddedKafka.partitionsPerTopic)

        // Sett opp producer
        val sedSendtProducerTemplate = settOppProducerTemplate()

        // produserer sedSendt meldinger på kafka
        produserSedHendelser(sedSendtProducerTemplate)

        // Venter på at sedListener skal consumeSedSendt meldingene
        sedListener.getLatch().await(10, TimeUnit.SECONDS)

        verifiser()

        container.stop()
        oppgaveContainer.stop()
        System.clearProperty("mockServerport")
    }

    private fun settOppProducerTemplate(): KafkaTemplate<Int, String> {
        val senderProps = KafkaTestUtils.producerProps(embeddedKafka.brokersAsString)
        val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
        val template = KafkaTemplate(pf)
        template.defaultTopic = SED_SENDT_TOPIC
        return template
    }

    private fun settOppUtitlityConsumer(topicNavn: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            "eessi-pensjon-group2",
            "false",
            embeddedKafka
        )
        consumerProperties["auto.offset.reset"] = "earliest"

        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProperties)
        val containerProperties = ContainerProperties(topicNavn)
        val container = KafkaMessageListenerContainer<String, String>(consumerFactory, containerProperties)
        val messageListener = MessageListener<String, String> { record -> println("Konsumerer melding:  $record") }
        container.setupMessageListener(messageListener)

        return container
    }

    private fun opprettForenkletSEDListe(file: String): List<ForenkletSED> {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    protected fun opprettBucDocuments(file: String): List<Document> {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    protected fun opprettSedDokument(file: String): SedDokumentfiler {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    protected fun <T> opprettSED(file: String, clazz: Class<T>): T {
        val json = javaClass.getResource(file).readText()
        return jacksonObjectMapper()
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(json, clazz)
    }

    protected fun opprettArbeidsFordeling(file: String): List<Norg2ArbeidsfordelingItem> {
        val json = javaClass.getResource(file).readText()
        return json.toKotlinObject()
    }

    companion object {
        init {
            // Start Mockserver in memory
            val port = Random().nextInt( 65535 - 1024) + 1024
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

            // Mocker STS
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

            // Mocker STS service discovery
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

    }

}

