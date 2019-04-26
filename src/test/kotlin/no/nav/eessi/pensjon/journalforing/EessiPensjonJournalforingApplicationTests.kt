package no.nav.eessi.pensjon.journalforing

import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("integrationtest")
class EessiPensjonJournalforingApplicationTests {

	@Test
	fun contextLoads() {
	}

	companion object {
		const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"

		@ClassRule @JvmField
		var embeddedKafka = EmbeddedKafkaRule(1, true, SED_SENDT_TOPIC)
	}

	@Test
	@Throws(Exception::class)
	fun `Send en melding pa topic`() {
		val senderProps = KafkaTestUtils.senderProps(embeddedKafka.embeddedKafka.brokersAsString)
		val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
		val template = KafkaTemplate(pf)
		template.defaultTopic = SED_SENDT_TOPIC
		template.sendDefault("{ \"id\" : 1, \"sedId\" : \"someSedId\", \"sektorKode\" : \"P\", \"bucType\" : \"FB_BUC_01\", \"rinaSakId\" : \"123\", \"avsenderId\" : \"avsenderid\", \"avsenderNavn\" : \"avsendernavn\", \"mottakerId\" : \"mottakerid\", \"mottakerNavn\" : \"mottakernavn\", \"rinaDokumentId\" : \"123\", \"rinaDokumentVersjon\" : \"1\", \"sedType\" : \"F001\", \"navBruker\" : \"12345678910\" }")
		template.sendDefault(0, 2, "{ \"id\" : 2, \"sedId\" : \"someSedId\", \"sektorKode\" : \"P\", \"bucType\" : \"FB_BUC_01\", \"rinaSakId\" : \"123\", \"avsenderId\" : \"avsenderid\", \"avsenderNavn\" : \"avsendernavn\", \"mottakerId\" : \"mottakerid\", \"mottakerNavn\" : \"mottakernavn\", \"rinaDokumentId\" : \"123\", \"rinaDokumentVersjon\" : \"1\", \"sedType\" : \"F001\", \"navBruker\" : \"12345678910\" }")
		template.send(SED_SENDT_TOPIC, 0, 2, "{ \"id\" : 3, \"sedId\" : \"someSedId\", \"sektorKode\" : \"P\", \"bucType\" : \"FB_BUC_03\", \"rinaSakId\" : \"123\", \"avsenderId\" : \"avsenderid\", \"avsenderNavn\" : \"avsendernavn\", \"mottakerId\" : \"mottakerid\", \"mottakerNavn\" : \"mottakernavn\", \"rinaDokumentId\" : \"123\", \"rinaDokumentVersjon\" : \"1\", \"sedType\" : \"F001\", \"navBruker\" : \"12345678910\" }")
	}
}