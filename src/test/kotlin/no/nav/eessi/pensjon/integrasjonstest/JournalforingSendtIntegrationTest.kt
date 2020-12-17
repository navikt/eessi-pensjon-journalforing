package no.nav.eessi.pensjon.integrasjonstest

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.personoppslag.personv3.BrukerMock
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.security.sts.STSClientConfig
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.mockserver.model.Parameter
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
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
import java.util.*
import java.util.concurrent.TimeUnit

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1"

private lateinit var mockServer : ClientAndServer

@SpringBootTest(classes = [ JournalforingSendtIntegrationTest.TestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(controlledShutdown = true, partitions = 1, topics = [SED_SENDT_TOPIC, SED_MOTTATT_TOPIC, OPPGAVE_TOPIC], brokerProperties= ["log.dir=out/embedded-kafkasendt"])
class JournalforingSendtIntegrationTest {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var sedListener: SedListener

    @Autowired
    lateinit var personV3Service: PersonV3Service

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {

        // Mock personV3
        capturePersonMock()

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
        sedListener.getLatch().await(20000, TimeUnit.MILLISECONDS)

        // Verifiserer alle kall
        verifiser()

        // Shutdown
        shutdown(container)
    }

    private fun produserSedHendelser(sedSendtProducerTemplate: KafkaTemplate<Int, String>) {
        // Sender 1 Foreldre SED til Kafka
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/FB_BUC_01_F001.json").readText())

        // Sender 5 Pensjon SED til Kafka
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText())
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json").readText())
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_05_X008.json").readText())
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json").readText())

        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json").readText())

        // Sender Sed med ugyldig FNR
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json").readText())

    }

    private fun shutdown(container: KafkaMessageListenerContainer<String, String>) {
        mockServer.stop()
        container.stop()
        embeddedKafka.kafkaServers.forEach { it.shutdown() }
    }

    private fun settOppProducerTemplate(): KafkaTemplate<Int, String> {
        val senderProps = KafkaTestUtils.producerProps(embeddedKafka.brokersAsString)
        val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
        val template = KafkaTemplate(pf)
        template.defaultTopic = SED_SENDT_TOPIC
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
            val port = randomFrom()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

            // Mocker STS
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withQueryStringParameter("grant_type", "client_credentials"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/STStoken.json"))))
                    )

            // Mocker Eux PDF generator
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/2536475861/sed/b12e06dda2c7474b9998c7139c77777/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))
                    )

            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedUgyldigMimeType.json"))))
                    )

            //Mock eux hent av sed
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))))
                    )

            //Mock eux hent sed R_BUC_02 -- R005 sed
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/2536475861/sed/b12e06dda2c7474b9998c7139c899999"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-AP.json"))))
                    )

            //Mock eux hent sed R_BUC_02 -- H070 sed
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/2536475861/sed/9498fc46933548518712e4a1d5133113"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/buc/H070-NAV.json"))))
                    )


            //Mock fagmodul /buc/{rinanr}/allDocuments - ugyldig FNR
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/7477291/allDocuments"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocuments_ugyldigFNR_ids.json"))))
                    )

            //Mock fagmodul /buc/{rinanr}/allDocuments - R_BUC
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/2536475861/allDocuments"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsidsR_BUC_02.json"))))
                    )

            //Mock fagmodul /buc/{rinanr}/allDocuments
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/.*/allDocuments"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json"))))
                    )

            //Mock eux hent av sed - ugyldig FNR
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx" ))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-ugyldigFNR-NAV.json"))))
                    )

            //Mock eux hent av sed
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/.*/sed/44cb68f89a2f4e748934fb4722721018" ))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json"))))
                    )

            //Mock eux hent av sed
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
                            .withPath("/buc/.*" ))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/buc/bucNorskCaseOwner.json"))))
                    )

            // Mocker journalføringstjeneste
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST.name)
                            .withPath("/journalpost"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json"))))
                            .withDelay(TimeUnit.SECONDS, 1)
                    )

            //Mock norg2tjeneste
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST.name)
                            .withPath("/api/v1/arbeidsfordeling")
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/norg2/norg2arbeidsfordeling4803request.json")))))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/norg2/norg2arbeidsfordelig4803result.json"))))
                    )

            // Mocker aktørregisteret
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.GET.name)
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
                    request()
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

            // Mocker bestemSak
            mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.POST.name)
                            .withPath("/")
                    )
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pen/bestemSakResponse.json"))))
                            .withDelay(TimeUnit.SECONDS, 1)
                    )

            // Mocker oppdaterDistribusjonsinfo
           mockServer.`when`(
                    request()
                            .withMethod(HttpMethod.PATCH.name)
                            .withPath("/journalpost/.*/oppdaterDistribusjonsinfo"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody("")
                            .withDelay(TimeUnit.SECONDS, 1)
                    )
        }

        private fun randomFrom(from : Int = 1024, to: Int = 65535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }

    private fun verifiser() {
        assertEquals(0, sedListener.getLatch().count, "Alle meldinger har ikke blitt konsumert")

        // Verifiserer at det har blitt forsøkt å hente PDF fra eux
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer"),
                VerificationTimes.once()
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer"),
                VerificationTimes.once()
        )

        //verify R_BUC_02_R004
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/2536475861/sed/b12e06dda2c7474b9998c7139c77777/filer"),
                VerificationTimes.once()
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx/filer"),
                VerificationTimes.atLeast(1)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer"),
                VerificationTimes.once()
        )

        // Verfiy fagmodul allDocuments R_BUC_02 R004 sed
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/2536475861/allDocuments"),
                VerificationTimes.once()
        )

        // Verfiy fagmodul allDocuments on Sed ugyldigFNR
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/7477291/allDocuments"),
                VerificationTimes.once()
        )

        // Verfiy fagmodul allDocuments
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/.*/allDocuments"),
                VerificationTimes.atLeast(4)
        )

        // Verfiy eux sed on ugyldig-FNR
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx"),
                VerificationTimes.atLeast(1)
        )

        //verify R_BUC_02 sed R005
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/2536475861/sed/b12e06dda2c7474b9998c7139c899999"),
                VerificationTimes.atLeast(1)
        )

        //verify R_BUC_02 sed H070
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/2536475861/sed/9498fc46933548518712e4a1d5133113"),
                VerificationTimes.atLeast(1)
        )


        // Verfiy eux sed
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET.name)
                        .withPath("/buc/.*/sed/44cb68f89a2f4e748934fb4722721018"),
                VerificationTimes.atLeast( 4 )
        )

        // Verifiserer at det har blitt forsøkt å opprette en journalpost
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST.name)
                        .withPath("/journalpost"),
                VerificationTimes.atLeast(4)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.PATCH.name)
                        .withPath("/journalpost/.*/oppdaterDistribusjonsinfo"),
                VerificationTimes.atLeast(1)
        )

        // Verifiser at det har blitt forsøkt å hente person fra tps
        verify(exactly = 23) { personV3Service.hentPerson(any()) }
    }

    // Mocks the PersonV3 Service so we don't have to deal with SOAP
    @TestConfiguration
    class TestConfig(private val stsClientConfig: STSClientConfig){
        @Bean
        @Primary
        fun personV3(): PersonV3 = mockk()

        @Bean
        fun personV3Service(personV3: PersonV3): PersonV3Service {
            return spyk(PersonV3Service(personV3, stsClientConfig))
        }
    }
}
