package no.nav.eessi.pensjon.integrasjonstest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.JournalpostMedSedInfo
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OpprettOppgaveService
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.listeners.SedSendtListener
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
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
import org.springframework.web.client.RestTemplate
import java.util.*
import java.util.concurrent.TimeUnit

const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"

abstract class IntegrasjonsBase {

    @Autowired
    lateinit var mottattListener: SedMottattListener
    @Autowired
    lateinit var opprettOppgaveService: OpprettOppgaveService
    @Autowired
    lateinit var sendtListener: SedSendtListener
    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired
    lateinit var journalpostService: JournalpostService
    @Autowired
    lateinit var journalforingService: JournalforingService

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
    }

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

        oppgaveContainer = settOppUtitlityConsumer(OPPGAVE_TOPIC)
        oppgaveContainer.start()
        ContainerTestUtils.waitForAssignment(oppgaveContainer, 2)

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
        val meldingFraLog = logsList.find { message ->
            message.message.contains("-oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding:") && message.message.contains(
                "\"journalpostId\" : \"$journalpostId\""
            )
        }?.message ?: ""

        fun medtildeltEnhetsnr(melding: String) = apply {
            assertTrue(meldingFraLog.contains("\"tildeltEnhetsnr\" : \"$melding\""))
        }

        fun medSedtype(melding: String) = apply {
            assertTrue(meldingFraLog.contains("\"sedType\" : \"$melding\""))
        }

        fun medHendelsetype(melding: String) = apply {
            assertTrue(meldingFraLog.contains("\"hendelseType\" : \"$melding\""))
        }
    }

    fun isMessageInlog(keyword: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.find { logMelding ->
            logMelding.message.contains(keyword)
        }?.message?.isNotEmpty() ?: false
    }


    fun startJornalforingForMottatt(messagePath: String) {
        // del 1: opprettelse av journalpost og oppgave: lagre jp-request før vurdering
        val journalpostRequest = slot<OpprettJournalpostRequest>()

        val hendelse = javaClass.getResource(messagePath)!!.readText()

        sedMottattTemplate.sendDefault(hendelse).get(20L, TimeUnit.SECONDS)
        mottattListener.getLatch().await(50, TimeUnit.SECONDS)
        Thread.sleep(5000)

        // del 2: sender manuel generering av JP og oppgave som batch / gcp storage ville gjort
        createMockedJournalPostWithOppgave(journalpostRequest, hendelse, HendelseType.MOTTATT)
    }
    fun createMockedJournalPostWithOppgave(
        journalpostRequest: CapturingSlot<OpprettJournalpostRequest>,
        hendelse: String,
        hendelseType: HendelseType
    ) {
        if (journalpostRequest.isCaptured && journalpostRequest.captured.bruker == null) {
            val lagretJournalpost = JournalpostMedSedInfo(
                journalpostRequest.captured,
                mapJsonToAny<SedHendelse>(hendelse),
                hendelseType
            )
            val response  = journalpostService.sendJournalPost(lagretJournalpost.journalpostRequest, lagretJournalpost.sedHendelse, lagretJournalpost.sedHendelseType, null)

            journalforingService.vurderSettAvbruttOgLagOppgave(
                Fodselsnummer.fra(lagretJournalpost.journalpostRequest.bruker?.id),
                lagretJournalpost.sedHendelseType,
                lagretJournalpost.sedHendelse,
                response,
                lagretJournalpost.journalpostRequest.journalfoerendeEnhet!!,
                lagretJournalpost.journalpostRequest.bruker?.id,
                lagretJournalpost.journalpostRequest.tema,
                saksInfoSamlet = SaksInfoSamlet()

            )
        }
    }
    fun startJornalforingForSendt(messagePath: String) {
        sedSendttTemplate.sendDefault(javaClass.getResource(messagePath)!!.readText()).get(20L, TimeUnit.SECONDS)
        sendtListener.getLatch().await(50, TimeUnit.SECONDS)
        Thread.sleep(5000)
    }


    fun CustomMockServer.mockBucResponse(endpoint: String, bucId: String, documentsPath: String?) = apply {
        mockHttpRequestWithResponseFromJson(
            endpoint,
            HttpMethod.GET,
            Buc(
                id = bucId,
                participants = emptyList(),
                documents = opprettBucDocuments(documentsPath!!)
            ).toJson()
        )
    }
    fun CustomMockServer.mockSedResponse(endpoint: String, responseFile: String) = apply {
        mockHttpRequestWithResponseFromFile(endpoint, HttpMethod.GET, responseFile)
    }

    fun CustomMockServer.mockFileResponse(endpoint: String, responseFile: String) = apply {
        mockHttpRequestWithResponseFromFile(endpoint, HttpMethod.GET, responseFile)
    }

    fun emptyResponse(): String = """{ "data": {}, "errors": null }"""


    fun setupMockServer(
        bucId: String,
        journalpostId: String,
        bucDocList: String? =  "/fagmodul/alldocumentsids.json",
        responses: List<Pair<String, String>>
    ) {
        CustomMockServer()
            .mockBucResponse("/buc/$bucId", bucId, bucDocList)
            .apply {
                responses.forEach { (endpoint, responseFile) ->
                    if (responseFile.contains("filer")) mockFileResponse(endpoint, responseFile)
                    else mockSedResponse(endpoint, responseFile)
                }
            }
            .medJournalforing(false, journalpostId)
            .medNorg2Tjeneste()
            .mockBestemSak()
    }


    fun verifyOppgave(journalpostId: String, hendelsetype: String, sedtype: String, enhetsnr: String) {
        OppgaveMeldingVerification(journalpostId)
            .medHendelsetype(hendelsetype)
            .medSedtype(sedtype)
            .medtildeltEnhetsnr(enhetsnr)
    }

}

