package no.nav.eessi.pensjon.journalforing

import io.mockk.slot
import io.mockk.*
import no.nav.eessi.pensjon.journalforing.services.kafka.SedMottattConsumer
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonMock
import org.junit.Test
import org.junit.runner.RunWith
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.Parameter
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.ws.rs.HttpMethod

private const val TOPIC = "eessi-basis-sedMottatt-v1"

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ SedIntegrationBaseTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
class SEDMottattIntegrationTest : SedIntegrationBaseTest() {

    @Autowired
    lateinit var sedMottattConsumer: SedMottattConsumer

    @Test
    @Throws(Exception::class)
    fun `Når en SEDSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {

        val slot = slot<String>()
        every { personV3Service.hentPerson(fnr = capture(slot)) } answers { PersonMock.createWith(slot.captured)!! }

        val consumerProperties = KafkaTestUtils.consumerProps("eessi-pensjon-group2",
                "false",
                embeddedKafka.embeddedKafka)

        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProperties)
        val containerProperties = ContainerProperties(TOPIC)
        val container = KafkaMessageListenerContainer<String, String>(consumerFactory, containerProperties)
        val messageListener = MessageListener<String, String> { record -> println("Konsumerer melding:  $record") }

        container.setupMessageListener(messageListener)
        container.start()

        // Vent til kafka er klar
        ContainerTestUtils.waitForAssignment(container, 1)

        // Sett opp produser
        val senderProps = KafkaTestUtils.senderProps(embeddedKafka.embeddedKafka.brokersAsString)
        val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
        val template = KafkaTemplate(pf)
        template.defaultTopic = TOPIC

        // Sender 1 Foreldre SED til Kafka
        System.out.println("Produserer FB_BUC_01 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/FB_BUC_01.json"))))

        // Sender 3 Pensjon SED til Kafka
        System.out.println("Produserer P_BUC_01 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))
        System.out.println("Produserer P_BUC_03 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))))
        System.out.println("Produserer P_BUC_05 melding")
        template.sendDefault(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_05.json"))))

        // Venter på at sedSendtConsumer skal consume meldingene
        sedMottattConsumer.getLatch().await(15000, TimeUnit.MILLISECONDS)

        // Verifiserer at det har blitt forsøkt å hente PDF fra eux
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer")
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                VerificationTimes.exactly(1)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer")
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                VerificationTimes.exactly(1)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.GET)
                        .withPath("/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer")
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
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
                                "}"),
        VerificationTimes.exactly(1)
        )

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave uten aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{\n" +
                                "  \"tildeltEnhetsnr\" : \"4303\",\n" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                "  \"journalpostId\" : \"string\",\n" +
                                "  \"beskrivelse\" : \"X008 - Ugyldiggjøre SED\",\n" +
                                "  \"tema\" : \"PEN\",\n" +
                                "  \"oppgavetype\" : \"JFR\",\n" +
                                "  \"prioritet\" : \"NORM\",\n" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," +"\n" +
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
                                "  \"tildeltEnhetsnr\" : \"4303\",\n" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",\n" +
                                "  \"journalpostId\" : \"string\",\n" +
                                "  \"beskrivelse\" : \"P2200 - Krav om uførepensjon\",\n" +
                                "  \"tema\" : \"PEN\",\n" +
                                "  \"oppgavetype\" : \"JFR\",\n" +
                                "  \"prioritet\" : \"NORM\",\n" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," +"\n" +
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

        // Verifiser at det har blitt forsøkt å hente person fra tps
        verify(exactly = 1) { personV3Service.hentPerson(any()) }


        container.stop()
    }
}