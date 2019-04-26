package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class SedConsumer {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)
    private val mapper = ObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse.")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)

        if(sedHendelse.sektorKode.equals("P")){
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
            acknowledgment.acknowledge()
        }
    }
}