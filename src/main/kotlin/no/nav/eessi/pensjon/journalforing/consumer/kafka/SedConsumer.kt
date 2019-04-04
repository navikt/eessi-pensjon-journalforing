package no.nav.eessi.pensjon.journalforing.consumer.kafka;

import no.nav.eessi.pensjon.journalforing.integration.model.SedHendelse
import org.codehaus.jackson.map.ObjectMapper
import org.slf4j.LoggerFactory

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service;

@Service
class SedConsumer : KafkaConsumer {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)
    private val mapper = ObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    override fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)

        if(sedHendelse.sektorKode == "P"){
            logger.info("Gjelder pensjon: $sedHendelse")
            acknowledgment.acknowledge()
        }
    }
}