package no.nav.eessi.pensjon.listeners

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import no.nav.eessi.pensjon.models.HendelseType.*
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

@Service
class SedListener(
        private val journalforingService: JournalforingService,
        private val personidentifiseringService: PersonidentifiseringService,
        private val sedDokumentHelper: SedDokumentHelper,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)
    private val sendtLatch = CountDownLatch(6)
    private val mottattLatch = CountDownLatch(7)
    private val mapper = jacksonObjectMapper()
    private val gyldigeInnkommendeHendelser = listOf("P", "H_BUC_07", "R_BUC_02")
    private val gyldigeUtgaendeHendelser = listOf("P", "R_BUC_02")

    fun getLatch() = sendtLatch
    fun getMottattLatch() = mottattLatch

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            metricsHelper.measure("consumeOutgoingSed") {
                logger.info("Innkommet sedSendt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug(hendelse)
                try {
                    val offset = cr.offset()

                    if (gyldigSendtHendelse(hendelse)) {
                        val sedHendelse = SedHendelseModel.fromJson(hendelse)
                        logger.info("*** Starter utgående journalføring for SED ${sedHendelse.sedType} BUCtype: ${sedHendelse.bucType} bucid: ${sedHendelse.rinaSakId} ***")
                        val alleSedIBuc  = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId)
                        val identifisertPerson = personidentifiseringService.identifiserPerson(sedHendelse, sedDokumentHelper.hentAlleSeds(alleSedIBuc))
                        val ytelseType = sedDokumentHelper.hentYtelseType(sedHendelse,alleSedIBuc)
                        journalforingService.journalfor(sedHendelse, SENDT, identifisertPerson, ytelseType, offset)
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
                sendtLatch.countDown()
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
                    val offset = cr.offset()

                    if (gyldigMottattHendelse(hendelse)) {
                            val sedHendelse = SedHendelseModel.fromJson(hendelse)
                            logger.info("*** Starter innkommende journalføring for SED ${sedHendelse.sedType} BUCtype: ${sedHendelse.bucType} bucid: ${sedHendelse.rinaSakId} ***")
                            val alleSedIBuc = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId)
                            val identifisertPerson = personidentifiseringService.identifiserPerson(sedHendelse, sedDokumentHelper.hentAlleSeds(alleSedIBuc))
                            val ytelseType = sedDokumentHelper.hentYtelseType(sedHendelse,alleSedIBuc)
                            journalforingService.journalfor(sedHendelse, MOTTATT, identifisertPerson, ytelseType, offset)
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
                mottattLatch.countDown()
            }
        }
    }

    fun gyldigMottattHendelse(hendelse: String) = getHendelseList(hendelse).map { gyldigeInnkommendeHendelser.contains( it ) }.contains(true)

    fun gyldigSendtHendelse(hendelse: String) = getHendelseList(hendelse).map { gyldigeUtgaendeHendelser.contains( it ) }.contains(true)

    private fun getHendelseList(hendelse: String): List<String> {
        val rootNode = mapper.readTree(hendelse)
        return listOf(rootNode.get("sektorKode").textValue(), rootNode.get("bucType").textValue())
    }

}
