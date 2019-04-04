package no.nav.eessi.pensjon.journalforing.consumer.kafka;

import org.slf4j.LoggerFactory

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

@Service
class SedConsumer : KafkaConsumer {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    override fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        if(hendelse.contains("P_BUC")){
            logger.info(hendelse)
        }
    }
}