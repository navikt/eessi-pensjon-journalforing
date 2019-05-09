package no.nav.eessi.pensjon.journalforing

import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpStatusCode
import org.mockserver.model.StringBody.exact
import org.mockserver.verify.VerificationTimes
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.ws.rs.HttpMethod

lateinit var mockServer : ClientAndServer

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("integrationtest")
class EessiPensjonJournalforingApplicationTests {

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
            // Mocker aktørregisteret
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET)
                            .withQueryStringParameter("/identer?identgruppe=AktoerId&gjeldende=true"))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("4567891235874")
                    )

        }

        fun randomFrom(from: Int = 1024, to: Int = 65535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }

    @Test
    @Throws(Exception::class)
    fun `Send en melding pa topic`() {
        // Set up kafka
        val senderProps = KafkaTestUtils.senderProps(embeddedKafka.embeddedKafka.brokersAsString)
        val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
        val template = KafkaTemplate(pf)
        template.defaultTopic = SED_SENDT_TOPIC

        // Sender 1 Foreldre SED til Kafka
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/FB_BUC_01.json"))))

        // Sender 2 Pensjon SED til Kafka
        template.sendDefault(0, 2, String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_01.json"))))
        template.send(SED_SENDT_TOPIC, 0, 2, String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_03.json"))))

        // Venter på at sedSendtConsumer skal consume meldingene
        Thread.sleep(5000)

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
        // Verifiserer at det har blitt forsøkt å opprette en journalpostoppgave
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/journalpost"),
                VerificationTimes.exactly(2)
        )
    }
}