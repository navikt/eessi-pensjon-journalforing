package no.nav.eessi.pensjon.journalforing.listeners

import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import no.nav.eessi.pensjon.journalforing.models.HendelseType.SENDT

@Service
class SedListener(private val journalforingService: JournalforingService) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)
    private val latch = CountDownLatch(4)

    private final val consumeSedSendtMessageNavn = "eessipensjon_journalforing.consumeOutgoingSed"
    private val consumeSedSendtMessageVellykkede = counter(consumeSedSendtMessageNavn, "vellykkede")
    private val consumeSedSendtMessageFeilede = counter(consumeSedSendtMessageNavn, "feilede")

    private final val consumeSedMottattMessageNavn = "eessipensjon_journalforing.consumeIncomingSed"
    private val consumeSedMottattMessageVellykkede = counter(consumeSedMottattMessageNavn, "vellykkede")
    private val consumeSedMottattMessageFeilede = counter(consumeSedMottattMessageNavn, "feilede")

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
            journalforingService.journalfor(hendelse, HendelseType.MOTTATT)
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