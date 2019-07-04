package no.nav.eessi.pensjon.journalforing.listeners

import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.journalforing.JournalforingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import no.nav.eessi.pensjon.journalforing.models.HendelseType.SENDT

@Service
class SedSendtConsumer(private val journalforingService: JournalforingService) {

    private val logger = LoggerFactory.getLogger(SedSendtConsumer::class.java)
    private val latch = CountDownLatch(4)

    private final val consumeSedMessageNavn = "eessipensjon_journalforing.consumeOutgoingSed"
    private val consumeSedMessageVellykkede = counter(consumeSedMessageNavn, "vellykkede")
    private val consumeSedMessageFeilede = counter(consumeSedMessageNavn, "feilede")

    fun getLatch(): CountDownLatch {
        return latch
    }

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet sedSendt hendelse")
        logger.debug(hendelse)
        try {
            journalforingService.journalfor(hendelse, SENDT)
            consumeSedMessageVellykkede.increment()
            acknowledgment.acknowledge()
        } catch(ex: Exception){
            consumeSedMessageFeilede.increment()
            logger.error(
                    "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                    "${ex.message}",
                    ex
            )
            throw RuntimeException(ex.message)
        }
        latch.countDown()
    }
}