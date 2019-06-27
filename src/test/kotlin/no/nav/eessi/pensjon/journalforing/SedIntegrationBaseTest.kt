package no.nav.eessi.pensjon.journalforing

import io.mockk.mockk
import io.mockk.spyk
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.junit.ClassRule
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.support.KafkaHeaders.TOPIC
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import javax.ws.rs.HttpMethod

lateinit var mockServer : ClientAndServer


open class SedIntegrationBaseTest {

    @Autowired
    lateinit var  personV3Service: PersonV3Service

    // Mocks the PersonV3 Service so we don't have to deal with SOAP
    @TestConfiguration
    class TestConfig{
        @Bean
        @Primary
        fun personV3(): PersonV3 = mockk()

        @Bean
        fun personV3Service(personV3: PersonV3): PersonV3Service {
            return spyk(PersonV3Service(personV3))
        }
    }

    companion object {

        @ClassRule
        @JvmField
        // Start kafka in memory
        var embeddedKafka = EmbeddedKafkaRule(1, true, TOPIC)


        init {
            // Start Mockserver in memory
            val port = randomFrom()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

            // Mocker STS
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withQueryStringParameter("grant_type", "client_credentials"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/STStoken.json"))))
                    )

            // Mocker Eux PDF generator
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )

            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )

            // Mocker journalføringstjeneste
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/journalpost"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("{\"journalpostId\": \"string\", \"journalstatus\": \"MIDLERTIDIG\", \"melding\": \"string\" }")
                    )

            // Mocker oppgavetjeneste
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"tildeltEnhetsnr\" : \"4862\",\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"aktoerId\" : \"1000101917358\",\n" +
                                    "  \"beskrivelse\" : \"P2000 - Krav om alderspensjon\",\n" +
                                    "  \"tema\" : \"PEN\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," +"\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"tildeltEnhetsnr\" : \"4476\",\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"aktoerId\" : \"1000101917358\",\n" +
                                    "  \"beskrivelse\" : \"P2000 - Krav om alderspensjon\",\n" +
                                    "  \"tema\" : \"PEN\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," +"\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"beskrivelse\" : \"P2000 - Krav om alderspensjon\",\n" +
                                    "  \"tema\" : \"PEN\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," +"\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{\n" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                    "  \"journalpostId\" : \"string\",\n" +
                                    "  \"beskrivelse\" : \"P2200 - Krav om uførepensjon\",\n" +
                                    "  \"tema\" : \"PEN\",\n" +
                                    "  \"oppgavetype\" : \"JFR\",\n" +
                                    "  \"prioritet\" : \"NORM\",\n" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," +"\n" +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" +"\n" +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            // Mocker aktørregisteret
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/identer")
                            .withQueryStringParameters(
                                    listOf(
                                            Parameter("identgruppe", "AktoerId"),
                                            Parameter("gjeldende", "true"))))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-1-gjeldende-NorskIdent.json"))))
                    )
            // Mocker STS service discovery
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/.well-known/openid-configuration"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(
                                    "{\n" +
                                            "  \"issuer\": \"http://localhost:${port}\",\n" +
                                            "  \"token_endpoint\": \"http://localhost:${port}/rest/v1/sts/token\",\n" +
                                            "  \"exchange_token_endpoint\": \"http://localhost:${port}/rest/v1/sts/token/exchange\",\n" +
                                            "  \"jwks_uri\": \"http://localhost:${port}/rest/v1/sts/jwks\",\n" +
                                            "  \"subject_types_supported\": [\"public\"]\n" +
                                            "}"
                            )
                    )

            // Mocker fagmodul hent ytelsetype
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/ytelseKravtype/161558/40b5723cd9284af6ac0581f3981f3044"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(
                                    "{}"
                            )
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/ytelseKravtype/148161/f899bf659ff04d20bc8b978b186f1ecc"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(
                                    "{}"
                            )
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/ytelseKravtype/147729/b12e06dda2c7474b9998c7139c841646"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(
                                    "{}"
                            )
                    )
        }

        private fun randomFrom(from: Int = 1024, to: Int = 65535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }
}