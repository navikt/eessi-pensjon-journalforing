package no.nav.eessi.pensjon.integrasjonstest

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1"

@SpringBootTest(classes = [ JournalforingSendtIntegrationTest.TestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(controlledShutdown = true, partitions = 1, topics = [SED_SENDT_TOPIC, SED_MOTTATT_TOPIC, OPPGAVE_TOPIC], brokerProperties= ["log.dir=out/embedded-kafkasendt"])
class JournalforingSendtIntegrationFB_BUC_01Test : JournalforingSendtIntegrationBase() {

    @Autowired
    lateinit var personService: PersonService

    @TestConfiguration
    class TestConfig {
        @Bean
        fun personService(): PersonService {
            return mockk(relaxed = true) {
                every { initMetrics() } just Runs
            }
        }
    }

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {
        initContainer()
    }

    override fun produserSedHendelser(sedSendtProducerTemplate: KafkaTemplate<Int, String>) {
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/FB_BUC_01_F001.json").readText())
    }

    override fun verifiser() {
        assertEquals(0, sedListener.getLatch().count, "Alle meldinger har ikke blitt konsumert")
    }
}