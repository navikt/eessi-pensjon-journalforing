package no.nav.eessi.pensjon.automatisering

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class StatistikkPublisher(private val statistikkKafkaTemplate: KafkaTemplate<String, String>) {

    private val logger = LoggerFactory.getLogger(StatistikkPublisher::class.java)

    fun publiserStatistikkMelding(statistikkMelding: StatistikkMelding) {
        logger.info("Produserer melding på kafka: ${statistikkKafkaTemplate.defaultTopic}  melding: $statistikkMelding")

        statistikkKafkaTemplate.send(KafkaStatistikkMessage(statistikkMelding)).get()
    }
}