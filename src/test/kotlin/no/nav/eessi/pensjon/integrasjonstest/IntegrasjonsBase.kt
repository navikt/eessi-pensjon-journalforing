package no.nav.eessi.pensjon.integrasjonstest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.listeners.SedSendtListener
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.web.client.RestTemplate
import java.util.*
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

    lateinit var mottattContainer: KafkaMessageListenerContainer<String, String>
    lateinit var oppgaveContainer: KafkaMessageListenerContainer<String, String>

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    lateinit var mockServer: ClientAndServer

    lateinit var oppgaveTemplate: KafkaTemplate<String, String>
    lateinit var sedMottattTemplate: KafkaTemplate<String, String>
    lateinit var sedSendttTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var fagmodulOidcRestTemplate : RestTemplate

    private fun settOppUtitlityConsumer(topicName: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            UUID.randomUUID().toString(),
            "false",
            embeddedKafka
        )
        consumerProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        consumerProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1
        val consumerFactory =
            DefaultKafkaConsumerFactory(consumerProperties, StringDeserializer(), StringDeserializer())

        return KafkaMessageListenerContainer(consumerFactory, ContainerProperties(topicName)).apply {
            setupMessageListener(MessageListener<String, String> { record -> println("Oppgaveintegrasjonstest konsumerer melding:  $record") })
        }
    }

    @BeforeEach
    fun setup() {

        println("*************************  BeforeEach START *****************************")

        listAppender.start()
        deugLogger.addAppender(listAppender)

        mottattContainer = settOppUtitlityConsumer(SED_MOTTATT_TOPIC)
        mottattContainer.start()
        Thread.sleep(1000) // wait a bit for the container to start

        ContainerTestUtils.waitForAssignment(mottattContainer, embeddedKafka.partitionsPerTopic)

        oppgaveContainer = settOppUtitlityConsumer(OPPGAVE_TOPIC)
        oppgaveContainer.start()
        ContainerTestUtils.waitForAssignment(oppgaveContainer, embeddedKafka.partitionsPerTopic)

        println("*************************  BeforeEach DONE *****************************")

        sedMottattTemplate = settOppProducerTemplate(SED_MOTTATT_TOPIC)
        sedSendttTemplate = settOppProducerTemplate(SED_SENDT_TOPIC)
        oppgaveTemplate = settOppProducerTemplate(OPPGAVE_TOPIC)

    }

    @AfterEach
    fun after() {
        println("************************* CLEANING UP AFTER CLASS*****************************")

        listAppender.stop()
        mottattContainer.stop()
        oppgaveContainer.stop()

        MockServerClient("localhost", System.getProperty("mockServerport").toInt()).reset()
    }

    protected fun opprettBucDocuments(file: String): List<DocumentsItem> {
        val json = javaClass.getResource(file)!!.readText()
        return mapJsonToAny(json)
    }

    private fun settOppProducerTemplate(topicNavn: String): KafkaTemplate<String, String> {
        val senderProps = KafkaTestUtils.producerProps(embeddedKafka.brokersAsString)
        return KafkaTemplate(DefaultKafkaProducerFactory(senderProps, StringSerializer(), StringSerializer())).apply {
            defaultTopic =topicNavn
        }
    }


    /**
     * Verifikasjons-tjeneste for oppgave
     */
    inner class OppgaveMeldingVerification(journalpostId: String) {
        val logsList: List<ILoggingEvent> = listAppender.list
        val meldingFraLog =
            logsList.find { message ->
                message.message.contains("-oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding:") && message.message.contains(
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

    fun isMessageInlog(keyword: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.find { logMelding ->
            logMelding.message.contains(keyword)
        }?.message?.isNotEmpty() ?: false
    }


    fun meldingForMottattListener(messagePath: String) {
        sedMottattTemplate.sendDefault(javaClass.getResource(messagePath)!!.readText()).get(20L, TimeUnit.SECONDS)
        mottattListener.getLatch().await(50, TimeUnit.SECONDS)
        Thread.sleep(5000)
    }
    fun meldingForSendtListener(messagePath: String) {
        sedSendttTemplate.sendDefault(javaClass.getResource(messagePath)!!.readText()).get(20L, TimeUnit.SECONDS)
        sendtListener.getLatch().await(50, TimeUnit.SECONDS)
        Thread.sleep(5000)
    }
}

