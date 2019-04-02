package no.nav.eessi.pensjon.journalforing.consumer.kafka;

import no.nav.eessi.pensjon.journalforing.integration.model.SedHendelse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

@Service
class SedConsumer : KafkaConsumer {

    @Value("\${kafka.sedSendt.topic}")
    lateinit var topic: String

    @Value("\${kafka.sedSendt.groupid}")
    lateinit var groupid: String


    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)

    @KafkaListener(topics = ["\${topic}"], groupId = "\${groupid}")
    override fun consume(hendelse: SedHendelse, headers: MessageHeaders, acknowledgment: Acknowledgment) {
        logger.info(hendelse.toString())
    }
}