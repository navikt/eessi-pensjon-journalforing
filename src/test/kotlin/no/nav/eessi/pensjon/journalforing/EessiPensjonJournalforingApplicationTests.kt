package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.journalforing.EessiPensjonJournalforingApplicationTests.Companion.SED_SENDT_TOPIC
import no.nav.eessi.pensjon.journalforing.EessiPensjonJournalforingApplicationTests.Companion.embeddedKafka
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpStatusCode
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import java.util.*
import javax.ws.rs.HttpMethod

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("integrationtest")
class EessiPensjonJournalforingApplicationTests {


	companion object {
		const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"

		@ClassRule @JvmField
		// Start kafka in memory
		var embeddedKafka = EmbeddedKafkaRule(1, true, SED_SENDT_TOPIC)


		init {
			// Start Mockserver in memory
			val port = randomFrom()
			val mockServer = ClientAndServer.startClientAndServer(port)
			System.setProperty("mockServerport", port.toString())

            // Mocker STS
			mockServer.`when`(
					request()
							.withMethod(HttpMethod.GET)
							//.withPath("/")
							.withQueryStringParameter("grant_type", "client_credentials")
			)
					.respond(
							response()
									.withHeader(Header("Content-Type", "application/json; charset=utf-8"))
									.withStatusCode(HttpStatusCode.OK_200.code())
									.withBody("{\"access_token\": \"sometoken\" , \"token_type\": \"sometype\", \"expires_in\": \"123456789\"}")
					)
            // Mocker Eux PDF generator
			mockServer.`when`(
					request()
							.withMethod(HttpMethod.GET)
							.withPath("/buc/123/sed/123/pdf")
			)
					.respond(
							response()
									.withHeader(Header("Content-Type", "application/json; charset=utf-8"))
									.withStatusCode(HttpStatusCode.OK_200.code())
									.withBody("thePdf")
					)

		}

		fun randomFrom(from: Int = 1024, to: Int = 65535) : Int {
			val random = Random()
			return random.nextInt(to - from) + from
		}
	}


	@Test
	fun contextLoads() {
	}

	@Test
	@Throws(Exception::class)
	fun `Send en melding pa topic`() {
		// Set up kafka
		val senderProps = KafkaTestUtils.senderProps(embeddedKafka.embeddedKafka.brokersAsString)
		val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
		val template = KafkaTemplate(pf)
		template.defaultTopic = SED_SENDT_TOPIC

		// Send to kafka
		template.sendDefault("{ \"id\" : 1, \"sedId\" : \"someSedId\", \"sektorKode\" : \"P\", \"bucType\" : \"FB_BUC_01\", \"rinaSakId\" : \"123\", \"avsenderId\" : \"avsenderid\", \"avsenderNavn\" : \"avsendernavn\", \"mottakerId\" : \"mottakerid\", \"mottakerNavn\" : \"mottakernavn\", \"rinaDokumentId\" : \"123\", \"rinaDokumentVersjon\" : \"1\", \"sedType\" : \"F001\", \"navBruker\" : \"12345678910\" }")
		template.sendDefault(0, 2, "{ \"id\" : 2, \"sedId\" : \"someSedId\", \"sektorKode\" : \"P\", \"bucType\" : \"FB_BUC_01\", \"rinaSakId\" : \"123\", \"avsenderId\" : \"avsenderid\", \"avsenderNavn\" : \"avsendernavn\", \"mottakerId\" : \"mottakerid\", \"mottakerNavn\" : \"mottakernavn\", \"rinaDokumentId\" : \"123\", \"rinaDokumentVersjon\" : \"1\", \"sedType\" : \"F001\", \"navBruker\" : \"12345678910\" }")
		template.send(SED_SENDT_TOPIC, 0, 2, "{ \"id\" : 3, \"sedId\" : \"someSedId\", \"sektorKode\" : \"P\", \"bucType\" : \"FB_BUC_03\", \"rinaSakId\" : \"123\", \"avsenderId\" : \"avsenderid\", \"avsenderNavn\" : \"avsendernavn\", \"mottakerId\" : \"mottakerid\", \"mottakerNavn\" : \"mottakernavn\", \"rinaDokumentId\" : \"123\", \"rinaDokumentVersjon\" : \"1\", \"sedType\" : \"F001\", \"navBruker\" : \"12345678910\" }")
	}



	fun initMockserver() {
	}
}