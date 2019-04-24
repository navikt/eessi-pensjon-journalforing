package no.nav.eessi.pensjon.journalforing

import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.junit.ClassRule
import org.springframework.test.context.ActiveProfiles


@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("integrationtest")
class EessiPensjonJournalforingApplicationTests {

	@Test
	fun contextLoads() {
	}


	companion object {
		val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"

		@ClassRule @JvmField
		var embeddedKafka = EmbeddedKafkaRule(1, true, SED_SENDT_TOPIC)
	}

	@Test
	@Throws(Exception::class)
	fun `Send en melding på topic`() {
		Thread.sleep(10000)

		val senderProps = KafkaTestUtils.senderProps(embeddedKafka.embeddedKafka.brokersAsString)
		val pf = DefaultKafkaProducerFactory<Int, String>(senderProps)
		val template = KafkaTemplate(pf)
		template.defaultTopic = SED_SENDT_TOPIC
		template.sendDefault("foo")
		template.sendDefault(0, 2, "bar")
		template.send(SED_SENDT_TOPIC, 0, 2, "baz")
	}
}
