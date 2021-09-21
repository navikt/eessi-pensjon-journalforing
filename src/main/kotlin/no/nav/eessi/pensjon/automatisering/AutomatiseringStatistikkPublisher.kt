package no.nav.eessi.pensjon.automatisering

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class AutomatiseringStatistikkPublisher(private val aivenAutomatiseringKafkaTemplate: KafkaTemplate<String, String>) {

    private val logger = LoggerFactory.getLogger(AutomatiseringStatistikkPublisher::class.java)

    fun publiserAutomatiseringStatistikk(automatiseringMelding: AutomatiseringMelding) {
        logger.info("Produserer melding på kafka: ${aivenAutomatiseringKafkaTemplate.defaultTopic}  melding: $automatiseringMelding")

        aivenAutomatiseringKafkaTemplate.send(KafkaAutomatiseringMessage(automatiseringMelding)).get()
    }
}