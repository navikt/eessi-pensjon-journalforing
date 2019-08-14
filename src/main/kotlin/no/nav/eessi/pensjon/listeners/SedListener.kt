package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.Metrics.*
import no.nav.eessi.pensjon.journalforing.JournalforingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import no.nav.eessi.pensjon.models.HendelseType.*

@Service
class SedListener(private val journalforingService: JournalforingService) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)
    private val latch = CountDownLatch(4)

    private val consumeSedSendtMessageVellykkede = counter("eessipensjon_journalforing", "http_request", "consumeOutgoingSed", "type", "vellykkede")
    private val consumeSedSendtMessageFeilede = counter("eessipensjon_journalforing", "http_request", "consumeOutgoingSed", "type", "feilede")

    private val consumeSedMottattMessageVellykkede = counter("eessipensjon_journalforing", "http_request","consumeIncomingSed", "type", "vellykkede")
    private val consumeSedMottattMessageFeilede = counter("eessipensjon_journalforing", "http_request","consumeIncomingSed", "type", "feilede")

    fun getLatch(): CountDownLatch {
        return latch
    }

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consumeSedSendt(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet sedSendt hendelse")
        logger.debug(hendelse)
        try {
            journalforingService.journalfor(hendelse, SENDT)
            consumeSedSendtMessageVellykkede.increment()
            acknowledgment.acknowledge()
        } catch(ex: Exception){
            consumeSedSendtMessageFeilede.increment()
            logger.error(
                    "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                    "${ex.message}",
                    ex
            )
            throw RuntimeException(ex.message)
        }
        latch.countDown()
    }

    @KafkaListener(topics = ["\${kafka.sedMottatt.topic}"], groupId = "\${kafka.sedMottatt.groupid}")
    fun consumeSedMottatt(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet sedMottatt hendelse")
        logger.debug(hendelse)
        try {
            journalforingService.journalfor(hendelse, MOTTATT)
            consumeSedMottattMessageVellykkede.increment()
            acknowledgment.acknowledge()
        } catch(ex: Exception){
            consumeSedMottattMessageFeilede.increment()
            logger.error(
                    "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                            "${ex.message}",
                    ex
            )
            throw RuntimeException(ex.message)
        }
    }
}
