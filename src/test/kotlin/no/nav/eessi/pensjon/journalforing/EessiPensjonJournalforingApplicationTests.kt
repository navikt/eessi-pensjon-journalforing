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
import java.util.*
import java.util.concurrent.TimeUnit
import javax.ws.rs.HttpMethod

lateinit var mockServer : ClientAndServer

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("integrationtest")
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
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveRequestMedAktoerIdPEN.json")))))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveRequestUtenAktoerIdPEN.json")))))
                    .respond(response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveRequestUtenAktoerIdUFO.json")))))
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

        // Sender 3 Pensjon SED til Kafka
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_01.json"))))
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_03.json"))))
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
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveRequestMedAktoerIdPEN.json")))),
        VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveRequestUtenAktoerIdPEN.json")))),
                VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette UFO oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveRequestUtenAktoerIdUFO.json")))),
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