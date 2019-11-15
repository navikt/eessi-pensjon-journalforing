package no.nav.eessi.pensjon

import io.mockk.slot
import io.mockk.*
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.services.personv3.BrukerMock
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.*
import org.mockserver.model.HttpRequest.request
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.ws.rs.HttpMethod

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"

private lateinit var mockServer : ClientAndServer

@SpringBootTest(classes = [ JournalforingIntegrationTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, topics = [SED_SENDT_TOPIC, SED_MOTTATT_TOPIC])
class JournalforingIntegrationTest {

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var sedListener: SedListener

    @Autowired
    lateinit var  personV3Service: PersonV3Service

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {

        // Mock personV3
        capturePersonMock()

        // Vent til kafka er klar
        val container = settOppUtitlityConsumer(SED_SENDT_TOPIC)
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        // Sett opp producer
        val sedSendtProducerTemplate = settOppProducerTemplate(SED_SENDT_TOPIC)

        // produserer sedSendt meldinger på kafka
        produserSedHendelser(sedSendtProducerTemplate)

        // Venter på at sedListener skal consumeSedSendt meldingene
        sedListener.getLatch().await(15000, TimeUnit.MILLISECONDS)

        // Verifiserer alle kall
        verifiser()

        // Shutdown
        shutdown(container)
    }

    private fun produserSedHendelser(sedSendtProducerTemplate: KafkaTemplate<Int, String>) {
        // Sender 1 Foreldre SED til Kafka
        sedSendtProducerTemplate.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01_F001.json"))))

        // Sender 3 Pensjon SED til Kafka
        sedSendtProducerTemplate.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json"))))
        sedSendtProducerTemplate.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03_P2200.json"))))
        sedSendtProducerTemplate.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_05_X008.json"))))
        sedSendtProducerTemplate.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000_MedUgyldigVedlegg.json"))))
    }

    private fun shutdown(container: KafkaMessageListenerContainer<String, String>) {
        mockServer.stop()
        container.stop()
        embeddedKafka.kafkaServers.forEach { it.shutdown() }
    }

    private fun settOppProducerTemplate(topicNavn: String): KafkaTemplate<Int, String> {
        val senderProps = KafkaTestUtils.senderProps(embeddedKafka.brokersAsString)
        val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
        val template = KafkaTemplate(pf)
        template.defaultTopic = topicNavn
        return template
    }

    private fun settOppUtitlityConsumer(topicNavn: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps("eessi-pensjon-group2",
                "false",
                embeddedKafka)
        consumerProperties["auto.offset.reset"] = "earliest"

        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProperties)
        val containerProperties = ContainerProperties(topicNavn)
        val container = KafkaMessageListenerContainer<String, String>(consumerFactory, containerProperties)
        val messageListener = MessageListener<String, String> { record -> println("Konsumerer melding:  $record") }
        container.setupMessageListener(messageListener)

        return container
    }

    private fun capturePersonMock() {
        val slot = slot<String>()
        every { personV3Service.hentPerson(fnr = capture(slot)) } answers { BrukerMock.createWith()!! }
    }

    companion object {

        init {
            // Start Mockserver in memory
            val lineSeparator = System.lineSeparator()
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
                            .withPath("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedUgyldigMimeType.json"))))
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
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )

            //Mock fagmodul /buc/{rinanr}/allDocuments
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/.*/allDocuments"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json"))))
                    )

            //Mock fagmodul hent fnr fra buc
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/sed/fodselsnr/161558/buctype/fjernes"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.FORBIDDEN_403.code())
                            .withBody("oops")
                    )

            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/sed/fodselsnr/148161/buctype/fjernes"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.FORBIDDEN_403.code())
                            .withBody("oops")
                    )

            //Mock eux hent av sed
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/buc/.*/sed/44cb68f89a2f4e748934fb4722721018" ))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json"))))
                    )



            // Mocker journalføringstjeneste
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/journalpost"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json"))))
                    )

            //Mock norg2tjeneste
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/api/v1/arbeidsfordeling")
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/norg2/norg2arbeidsfordeling4803request.json")))))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/norg2/norg2arbeidsfordelig4803result.json"))))
                    )


            // Mocker oppgavetjeneste
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{$lineSeparator"+
                                    "  \"tildeltEnhetsnr\" : \"4803\",$lineSeparator"  +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                    "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 147729\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
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
                            .withBody("{$lineSeparator" +
                                    "  \"tildeltEnhetsnr\" : \"4476\",$lineSeparator" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                    "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 147729\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
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
                            .withBody("{$lineSeparator" +
                                    "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående P2200 - Krav om uførepensjon / Rina saksnr: 148161\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
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
                            .withBody("{$lineSeparator" +
                                    "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående X008 - Ugyldiggjøre SED / Rina saksnr: 161558\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
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
                            .withBody("{$lineSeparator" +
                                    "  \"tildeltEnhetsnr\" : \"4803\",$lineSeparator" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                    "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 147666\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
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
                            .withBody("{$lineSeparator" +
                                    "  \"tildeltEnhetsnr\" : \"4803\",$lineSeparator" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Mottatt vedlegg: etWordDokument.doxc  tilhørende RINA sakId: 147666 er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff )\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"BEH_SED\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
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
                                            "  \"issuer\": \"http://localhost:$port\",\n" +
                                            "  \"token_endpoint\": \"http://localhost:$port/rest/v1/sts/token\",\n" +
                                            "  \"exchange_token_endpoint\": \"http://localhost:$port/rest/v1/sts/token/exchange\",\n" +
                                            "  \"jwks_uri\": \"http://localhost:$port/rest/v1/sts/jwks\",\n" +
                                            "  \"subject_types_supported\": [\"public\"]\n" +
                                            "}"
                            )
                    )
            // Mocker fagmodul hent ytelsetype
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.GET)
                            .withPath("/sed/ytelseKravtype/161558/sedid/40b5723cd9284af6ac0581f3981f3044"))
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
                            .withPath("/sed/ytelseKravtype/148161/sedid/f899bf659ff04d20bc8b978b186f1ecc"))
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
                            .withPath("/sed/ytelseKravtype/147729/sedid/b12e06dda2c7474b9998c7139c841646"))
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

    private fun verifiser() {
        val lineSeparator = System.lineSeparator()
        assertEquals(0, sedListener.getLatch().count, "Alle meldinger har ikke blitt konsumert")

        // Verifiserer at det har blitt forsøkt å hente PDF fra eux
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer"),
                VerificationTimes.once()
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer"),
                VerificationTimes.once()
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer"),
                VerificationTimes.once()
        )


        // Verfiy fagmodul allDocuments
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/.*/allDocuments"),
                VerificationTimes.atLeast(4)
        )
        // Verfiy eux sed
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/.*/sed/44cb68f89a2f4e748934fb4722721018"),
                VerificationTimes.atLeast(4)
        )

        // Verifiserer at det har blitt forsøkt å opprette en journalpost
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/journalpost"),
                VerificationTimes.exactly(4)
        )

        // Verifiserer at det har blitt forsøkt å hente enhet fra Norg2
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/api/v1/arbeidsfordeling")
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/norg2/norg2arbeidsfordeling4803request.json")))),
                VerificationTimes.exactly(2)
        )

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave med aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{$lineSeparator" +
                                "  \"tildeltEnhetsnr\" : \"4803\",$lineSeparator" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 147729\",$lineSeparator" +
                                "  \"tema\" : \"PEN\",$lineSeparator" +
                                "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                "}"),
                VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{$lineSeparator" +
                                "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                "  \"beskrivelse\" : \"Utgående X008 - Ugyldiggjøre SED / Rina saksnr: 161558\",$lineSeparator" +
                                "  \"tema\" : \"PEN\",$lineSeparator" +
                                "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                "}"),
                VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette UFO oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{$lineSeparator" +
                                "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                "  \"beskrivelse\" : \"Utgående P2200 - Krav om uførepensjon / Rina saksnr: 148161\",$lineSeparator" +
                                "  \"tema\" : \"PEN\",$lineSeparator" +
                                "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                "}"),
                VerificationTimes.exactly(1)
        )

        // Verifiser at det har blitt forsøkt å hente person fra tps
        verify(exactly = 18) { personV3Service.hentPerson(any()) }
    }

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
}
