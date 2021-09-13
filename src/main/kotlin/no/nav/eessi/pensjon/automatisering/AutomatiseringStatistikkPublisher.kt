package no.nav.eessi.pensjon.automatisering

import no.nav.eessi.pensjon.json.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class AutomatiseringStatistikkPublisher(private val kafkaTemplate: KafkaTemplate<String, String>,
                                        @Value("\${KAFKA_AUTOMATISERING_TOPIC}") private val automatiseringTopic: String
) {

    private val logger = LoggerFactory.getLogger(AutomatiseringStatistikkPublisher::class.java)

    fun publiserAutomatiseringStatistikk(automatiseringMelding: AutomatiseringMelding) {
        logger.info("Produserer melding på kafka: $automatiseringTopic  melding: $automatiseringMelding")

        kafkaTemplate.send(automatiseringTopic, automatiseringMelding.toJson()).get()
    }
}