package no.nav.eessi.pensjon.integrasjonstest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Document
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
    lateinit var personService: PersonService

    @Autowired
    lateinit var euxService: EuxService

    @TestConfiguration
    class TestConfig {
        @Bean
        fun personService(): PersonService {
            return mockk(relaxed = true) {
                every { initMetrics() } just Runs
            }
        }

        @Bean
        fun euxService(): EuxService {
            return spyk(EuxService(mockk(), MetricsHelper(SimpleMeterRegistry())))
//            return mockk(relaxed = true)
        }
    }
    @Disabled
    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {
        initMocks()

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
        sedListener.getLatch().await(25000, TimeUnit.MINUTES)

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

/*
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json").readText())
*/

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

    private fun initMocks() {
        // Mock PDL Person
        every { personService.hentPerson(NorskIdent("09035225916")) }
            .answers { PersonMock.createWith(fnr = "09035225916", aktoerId = AktoerId("1000101917358")) }

        every { personService.harAdressebeskyttelse(any(), any()) }
            .answers { false }

        // Mock EUX Service hentBuc
        every { euxService.hentBuc("7477291") }
            .answers {
                Buc(
                    id ="7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocuments_ugyldigFNR_ids.json")
                )
            }

        every { euxService.hentBuc("2536475861") }
            .answers {
                Buc(
                    id ="2536475861",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsidsR_BUC_02.json")
                )
            }

        every { euxService.hentBuc(any()) }
            .answers {
                Buc(
                    id ="12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                )
            }


        // Mock EUX Service (uthenting av alle sed i buc)
//        every { euxService.hentBucDokumenter(any()) }
//            .answers { opprettForenkletSEDListe("/fagmodul/alldocumentsids.json") }
//
//        every { euxService.hentBucDokumenter(Buc(id ="7477291")) }
//            .answers { opprettForenkletSEDListe("/fagmodul/alldocuments_ugyldigFNR_ids.json") }
//
//        every { euxService.hentBucDokumenter(Buc(id ="2536475861")) }
//            .answers { opprettForenkletSEDListe("/fagmodul/alldocumentsidsR_BUC_02.json") }


        // Mock EUX Service (FILER / VEDLEGG)
        every { euxService.hentAlleDokumentfiler("7477291", "b12e06dda2c7474b9998c7139c841646fffx") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxService.hentAlleDokumentfiler("147729", "b12e06dda2c7474b9998c7139c841646") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxService.hentAlleDokumentfiler("148161", "f899bf659ff04d20bc8b978b186f1ecc") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxService.hentAlleDokumentfiler("161558", "40b5723cd9284af6ac0581f3981f3044") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxService.hentAlleDokumentfiler("2536475861", "b12e06dda2c7474b9998c7139c77777") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxService.hentAlleDokumentfiler("147666", "b12e06dda2c7474b9998c7139c666666") }
            .answers { opprettSedDokument("/pdf/pdfResponseMedUgyldigMimeType.json") }

        // Mock EUX Service (SEDer)
        every { euxService.hentSed(any(), "44cb68f89a2f4e748934fb4722721018", ) }
            .answers { opprettSED("/sed/P2000-NAV.json", SED::class.java) }

        every { euxService.hentSed("161558", "40b5723cd9284af6ac0581f3981f3044", ) }
            .answers { opprettSED("/eux/SedResponseP2000.json", SED::class.java) }

        every { euxService.hentSed("148161", "f899bf659ff04d20bc8b978b186f1ecc", ) }
            .answers { opprettSED("/eux/SedResponseP2000.json", SED::class.java) }

        every { euxService.hentSed("147729", "b12e06dda2c7474b9998c7139c841646", ) }
            .answers { opprettSED("/eux/SedResponseP2000.json", SED::class.java) }

        every { euxService.hentSed("147666", "b12e06dda2c7474b9998c7139c666666", ) }
            .answers { opprettSED("/eux/SedResponseP2000.json", SED::class.java) }

        every { euxService.hentSed("2536475861", "b12e06dda2c7474b9998c7139c899999", ) }
            .answers { opprettSED("/sed/R_BUC_02-R005-AP.json", R005::class.java) }

        every { euxService.hentSed("2536475861", "9498fc46933548518712e4a1d5133113", ) }
            .answers { opprettSED("/buc/H070-NAV.json", SED::class.java) }

        every { euxService.hentSed("7477291", "b12e06dda2c7474b9998c7139c841646fffx", ) }
            .answers { opprettSED("/sed/P2000-ugyldigFNR-NAV.json", SED::class.java) }
    }

    private fun opprettForenkletSEDListe(file: String): List<ForenkletSED> {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    private fun opprettBucDocuments(file: String): List<Document> {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    private fun opprettSedDokument(file: String): SedDokumentfiler {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    private fun <T> opprettSED(file: String, clazz: Class<T>): T {
        val json = javaClass.getResource(file).readText()
        return jacksonObjectMapper()
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(json, clazz)
    }

    private inline fun <reified T: Any> String.toKotlinObject(): T {
        val mapper = jacksonObjectMapper()
        return mapper.readValue(this, T::class.java)
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


            //Mock eux hent av sed
//            mockServer.`when`(
//                    request()
//                            .withMethod(HttpMethod.GET.name)
//                            .withPath("/buc/.*" ))
//                    .respond(HttpResponse.response()
//                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
//                            .withStatusCode(HttpStatusCode.OK_200.code())
//                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/eux/buc/bucNorskCaseOwner.json"))))
//                    )

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


        // Verfiser uthenting av alle seder fra buc
//        verify(exactly = 1) { euxService.hentBuc("2536475861") }
//        verify(exactly = 1) { euxService.hentBuc("7477291") }
//        verify(atLeast = 4) { euxService.hentBuc(any()) }

//        verify(exactly = 1) { euxService.hentBucDokumenter(Buc(id ="2536475861")) }
//        verify(exactly = 1) { euxService.hentBucDokumenter(Buc(id ="7477291")) }
//        verify(atLeast = 6) { euxService.hentBucDokumenter(any()) }

        // Verifiserer at det har blitt forsøkt å hente PDF fra eux
        verify (exactly = 1) { euxService.hentAlleDokumentfiler("147729", "b12e06dda2c7474b9998c7139c841646") }
        verify (exactly = 1) { euxService.hentAlleDokumentfiler("148161", "f899bf659ff04d20bc8b978b186f1ecc") }
        verify (exactly = 1) { euxService.hentAlleDokumentfiler("2536475861", "b12e06dda2c7474b9998c7139c77777") }
        verify (atLeast = 1) { euxService.hentAlleDokumentfiler("7477291", "b12e06dda2c7474b9998c7139c841646fffx") }
        verify (exactly = 1) { euxService.hentAlleDokumentfiler("161558", "40b5723cd9284af6ac0581f3981f3044") }

        // Verifiser uthenting av SEDer
        verify (exactly = 1) { euxService.hentSed("7477291", "b12e06dda2c7474b9998c7139c841646fffx", ) }
        verify (exactly = 1) { euxService.hentSed("2536475861", "b12e06dda2c7474b9998c7139c899999", ) }
        verify (exactly = 1) { euxService.hentSed("2536475861", "9498fc46933548518712e4a1d5133113", ) }
        verify (atLeast = 4) { euxService.hentSed(any(), "44cb68f89a2f4e748934fb4722721018", ) }

        // Verifiser at det har blitt forsøkt å hente person fra tps
        verify(exactly = 5) { personService.hentPerson(any<Ident<*>>()) }
    }
}
