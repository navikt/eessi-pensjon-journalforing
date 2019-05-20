package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.journalforing.services.kafka.SedSendtConsumer
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpStatusCode
import org.mockserver.model.Parameter
import org.mockserver.model.StringBody.exact
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.ws.rs.HttpMethod

lateinit var mockServer : ClientAndServer

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("integrationtest")
@DirtiesContext
class EessiPensjonJournalforingApplicationTests {

    @Autowired
    lateinit var sedSendtConsumer: SedSendtConsumer

    companion object {
        const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"

        @ClassRule
        @JvmField
        // Start kafka in memory
        var embeddedKafka = EmbeddedKafkaRule(1, true, SED_SENDT_TOPIC)


        init {
            // Start Mockserver in memory
            val port = randomFrom()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

            // Mocker STS
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET)
                            .withQueryStringParameter("grant_type", "client_credentials"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/STStoken.json"))))
                    )

            // Mocker Eux PDF generator
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/pdf"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("pdf for P_BUC_01")
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/pdf"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("pdf for P_BUC_03")
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/pdf"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("pdf for P_BUC_05")
                    )

            // Mocker journalføringstjeneste
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/journalpost"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("{\"journalpostId\": \"string\", \"journalstatus\": \"MIDLERTIDIG\", \"melding\": \"string\" }")
                    )

            // Mocker oppgavetjeneste
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"aktoerId\" : \"1000101917358\",\n" +
                                    "  \"tema\" : \"PEN\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"tema\" : \"PEN\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"tema\" : \"UFO\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            // Mocker aktørregisteret
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/identer")
                            .withQueryStringParameters(
                                    listOf(
                                            Parameter("identgruppe", "AktoerId"),
                                            Parameter("gjeldende", "true"))))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-1-gjeldende-NorskIdent.json"))))
                    )

            // Waiting for kafka to be ready
            Thread.sleep(10000)
        }

        fun randomFrom(from: Int = 1024, to: Int = 65535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }

    @Test
    @Throws(Exception::class)
    fun `Send en melding pa topic`() {
        val consumerProperties = KafkaTestUtils.consumerProps("eessi-pensjon-group2",
                "false",
                embeddedKafka.embeddedKafka)
        // set up the Kafka consumer properties


        // create a Kafka consumer factory
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(
                consumerProperties)

        // set the topic that needs to be consumed
        val containerProperties = ContainerProperties(SED_SENDT_TOPIC)

        var container = KafkaMessageListenerContainer<String, String>(consumerFactory, containerProperties)

        // setup a Kafka message listener
        val messageListener = object : MessageListener<String, String> {
            override fun onMessage(record : ConsumerRecord<String, String>) {
                System.out.println("test-listener received message:  $record")
            }
        }

        container.setupMessageListener(messageListener)

        // start the container and underlying message listener
        container.start()
        // wait until the container has the required number of assigned partitions
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.embeddedKafka.partitionsPerTopic)
        // Set up kafka
        val senderProps = KafkaTestUtils.senderProps(embeddedKafka.embeddedKafka.brokersAsString)
        val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
        val template = KafkaTemplate(pf)
        template.defaultTopic = SED_SENDT_TOPIC
        // Sender 1 Foreldre SED til Kafka
        System.out.println("Produserer FB_BUC_01 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/FB_BUC_01.json"))))

        // Sender 3 Pensjon SED til Kafka
        System.out.println("Produserer P_BUC_01 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_01.json"))))
        System.out.println("Produserer P_BUC_03 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_03.json"))))
        System.out.println("Produserer P_BUC_05 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_05.json"))))

        // Venter på at sedSendtConsumer skal consume meldingene
        sedSendtConsumer.getLatch().await(15000, TimeUnit.MILLISECONDS)

        // Verifiserer at det har blitt forsøkt å hente PDF fra eux
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/pdf")
                        .withBody(exact("pdf for P_BUC_01")),
                VerificationTimes.exactly(1)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/pdf")
                        .withBody(exact("pdf for P_BUC_03")),
                VerificationTimes.exactly(1)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/pdf")
                        .withBody(exact("pdf for P_BUC_05")),
                VerificationTimes.exactly(1)
        )
        // Verifiserer at det har blitt forsøkt å opprette en journalpost
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/journalpost"),
                VerificationTimes.exactly(3)
        )

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave med aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{\n" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                "  \"journalpostId\" : \"string\",\n" +
                                "  \"aktoerId\" : \"1000101917358\",\n" +
                                "  \"tema\" : \"PEN\",\n" +
                                "  \"oppgavetype\" : \"JFR\",\n" +
                                "  \"prioritet\" : \"NORM\",\n" +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                "}"),
        VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{\n" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                "  \"journalpostId\" : \"string\",\n" +
                                "  \"tema\" : \"PEN\",\n" +
                                "  \"oppgavetype\" : \"JFR\",\n" +
                                "  \"prioritet\" : \"NORM\",\n" +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                "}"),
                VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette UFO oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{\n" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                "  \"journalpostId\" : \"string\",\n" +
                                "  \"tema\" : \"UFO\",\n" +
                                "  \"oppgavetype\" : \"JFR\",\n" +
                                "  \"prioritet\" : \"NORM\",\n" +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                "}"),
                VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å hente AktoerID
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/identer")
                        .withQueryStringParameters(
                            listOf(
                                Parameter("identgruppe", "AktoerId"),
                                Parameter("gjeldende", "true"))),
                VerificationTimes.exactly(1)
        )
    }
}