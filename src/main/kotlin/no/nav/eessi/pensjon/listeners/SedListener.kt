package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import no.nav.eessi.pensjon.models.HendelseType.*
import no.nav.eessi.pensjon.personidentifisering.IdentifiserPersonHelper
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

@Service
class SedListener(
        private val journalforingService: JournalforingService,
        private val identifiserPersonHelper: IdentifiserPersonHelper,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)
    private val latch = CountDownLatch(6)

    fun getLatch(): CountDownLatch {
        return latch
    }


    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            metricsHelper.measure("consumeOutgoingSed") {
                logger.info("Innkommet sedSendt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug(hendelse)
                try {
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)

                    if (sedHendelse.sektorKode == "P") {
                        val identifisertPerson = identifiserPersonHelper.identifiserPerson(sedHendelse)
                        journalforingService.journalfor(sedHendelse, SENDT, identifisertPerson)
                    }
                    acknowledgment.acknowledge()
                    logger.info("Acket sedSendt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                } catch (ex: Exception) {
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
    }

    @KafkaListener(topics = ["\${kafka.sedMottatt.topic}"], groupId = "\${kafka.sedMottatt.groupid}")
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            metricsHelper.measure("consumeIncomingSed") {
                logger.info("Innkommet sedMottatt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug(hendelse)
                try {
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)

                    if (sedHendelse.sektorKode == "P") {
                        val identifisertPerson = identifiserPersonHelper.identifiserPerson(sedHendelse)
                        journalforingService.journalfor(sedHendelse, MOTTATT, identifisertPerson)
                    }
                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                } catch (ex: Exception) {
                    logger.error(
                            "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                                    "${ex.message}",
                            ex
                    )
                    throw RuntimeException(ex.message)
                }
            }
        }
    }
}
