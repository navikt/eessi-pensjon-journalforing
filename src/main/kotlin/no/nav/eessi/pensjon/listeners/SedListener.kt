package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.PensjonSakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Service
class SedListener(
        private val journalforingService: JournalforingService,
        private val personidentifiseringService: PersonidentifiseringService,
        private val sedDokumentHelper: SedDokumentHelper,
        private val gyldigeHendelser: GyldigeHendelser,
        private val bestemSakKlient: BestemSakKlient,
        private val gyldigeFunksjoner: GyldigFunksjoner,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)
    private val sendtLatch = CountDownLatch(7)
    private val mottattLatch = CountDownLatch(8)

    private lateinit var consumeOutgoingSed: MetricsHelper.Metric
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        consumeOutgoingSed = metricsHelper.init("consumeOutgoingSed")
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    fun getLatch() = sendtLatch
    fun getMottattLatch() = mottattLatch

    @KafkaListener(id = "sedSendtListener",
            idIsGroup = false,
            topics = ["\${kafka.sedSendt.topic}"],
            groupId = "\${kafka.sedSendt.groupid}",
            autoStartup = "false")
    fun consumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeOutgoingSed.measure {
                logger.info("Innkommet sedSendt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug(hendelse)
                try {
                    val offset = cr.offset()

                    val sedHendelse = SedHendelseModel.fromJson(hendelse)
                    if (gyldigeHendelser.sendtHendelse(sedHendelse)) {
                        logger.info("*** Starter utgående journalføring for SED ${sedHendelse.sedType} BUCtype: ${sedHendelse.bucType} bucid: ${sedHendelse.rinaSakId} ***")
                        val alleSedIBuc = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId)
                        val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(sedHendelse.navBruker, sedDokumentHelper.hentAlleSeds(alleSedIBuc), sedHendelse.bucType)
                        val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBuc.values.toList())
                        val ytelseTypeFraSED = sedDokumentHelper.hentYtelseType(sedHendelse, alleSedIBuc)
                        val pensjonSakInformasjon = pensjonSakInformasjonSendt(identifisertPerson, sedHendelse, ytelseTypeFraSED, alleSedIBuc)
                        val ytelseType = populerYtelseType(ytelseTypeFraSED, pensjonSakInformasjon, sedHendelse, SENDT)
                        journalforingService.journalfor(sedHendelse, SENDT, identifisertPerson, fdato, ytelseType, offset, pensjonSakInformasjon)
                    }
                    acknowledgment.acknowledge()
                    logger.info("Acket sedSendt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av sendt SED-hendelse:\n $hendelse \n", ex)
                    throw JournalforingException(ex)
                }
                sendtLatch.countDown()
            }
        }
    }

    @KafkaListener(id = "sedMottattListener",
            idIsGroup = false,
            topics = ["\${kafka.sedMottatt.topic}"],
            groupId = "\${kafka.sedMottatt.groupid}",
            autoStartup = "false")
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeIncomingSed.measure {

                logger.info("Innkommet sedMottatt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug(hendelse)

                try {
                    val offset = cr.offset()
                    if (offset == 38518L) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {

                        logger.info("*** Offset $offset  Partition ${cr.partition()} ***")
                        val sedHendelse = SedHendelseModel.fromJson(hendelse)
                        if (gyldigeHendelser.mottattHendelse(sedHendelse)) {
                            logger.info("*** Starter innkommende journalføring for SED ${sedHendelse.sedType} BUCtype: ${sedHendelse.bucType} bucid: ${sedHendelse.rinaSakId} ***")
                            val alleSedIBuc = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId)
                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(sedHendelse.navBruker, sedDokumentHelper.hentAlleSeds(alleSedIBuc), sedHendelse.bucType)
                            val ytelseTypeFraSED = sedDokumentHelper.hentYtelseType(sedHendelse, alleSedIBuc)
                            val pensjonSakInformasjon = pensjonSakInformasjonMottatt(identifisertPerson, sedHendelse)
                            val ytelseType = populerYtelseType(ytelseTypeFraSED, pensjonSakInformasjon, sedHendelse, MOTTATT)
                            val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBuc.values.toList())

                            journalforingService.journalfor(sedHendelse, MOTTATT, identifisertPerson, fdato, ytelseType, offset, pensjonSakInformasjon)
                        }
                    }

                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av mottatt SED-hendelse:\n $hendelse \n", ex)
                    throw JournalforingException(ex)
                }
                mottattLatch.countDown()
            }
        }
    }

    private fun pensjonSakInformasjonMottatt(identifisertPerson: IdentifisertPerson?, sedHendelseModel: SedHendelseModel): PensjonSakInformasjon {
        if (identifisertPerson?.aktoerId == null) return PensjonSakInformasjon()

        return if (sedHendelseModel.bucType == BucType.P_BUC_02) {
            val sakInformasjon = bestemSakKlient.hentSakInformasjon(identifisertPerson.aktoerId, sedHendelseModel.bucType, identifisertPerson.personRelasjon.ytelseType)
            PensjonSakInformasjon(sakInformasjon)
        } else {
            PensjonSakInformasjon()
        }

    }

    private fun pensjonSakInformasjonSendt(identifisertPerson: IdentifisertPerson?, sedHendelseModel: SedHendelseModel, ytelsestypeFraSed: YtelseType?, alleSedIBuc: Map<String, String?>): PensjonSakInformasjon {
        if (identifisertPerson?.aktoerId == null) return PensjonSakInformasjon()

        val aktoerId = identifisertPerson.aktoerId
        val sakInformasjon = bestemSakKlient.hentSakInformasjon(aktoerId, sedHendelseModel.bucType, populerYtelsestypeSakInformasjonSendt(ytelsestypeFraSed, identifisertPerson))

        val pensjonSak = when (gyldigeFunksjoner.togglePensjonSak()) {
            true -> {
                logger.debug("skal hente pensjonSak for sed kap.1 og validere mot pesys")
                if (sakInformasjon == null) {
                    sedDokumentHelper.hentPensjonSakFraSED(aktoerId, alleSedIBuc)
                } else {
                    null
                }
            }
            false -> null
        }
        return PensjonSakInformasjon(sakInformasjon, pensjonSak)
    }

    private fun populerYtelsestypeSakInformasjonSendt(ytelsestypeFraSed: YtelseType?, identifisertPerson: IdentifisertPerson?): YtelseType? {
        if (ytelsestypeFraSed != null)
            return ytelsestypeFraSed
        return identifisertPerson?.personRelasjon?.ytelseType
    }

    private fun populerYtelseType(ytelseTypeFraSED: YtelseType?, pensjonSakInformasjon: PensjonSakInformasjon, sedHendelseModel: SedHendelseModel, hendelseType: HendelseType): YtelseType? {
        val sakInformasjon = pensjonSakInformasjon.sakInformasjon
        if (sedHendelseModel.bucType == BucType.P_BUC_02 && hendelseType == SENDT && sakInformasjon != null && sakInformasjon.sakType == YtelseType.UFOREP && sakInformasjon.sakStatus == SakStatus.AVSLUTTET) {
            return null
        } else if (ytelseTypeFraSED != null)
            return ytelseTypeFraSED
        return sakInformasjon?.sakType
    }

//    @KafkaListener(groupId = "\${kafka.sedSendt.groupid}-recovery",
//            topicPartitions = [TopicPartition(topic = "\${kafka.sedSendt.topic}",
//                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "25196")])])
//    fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
//        if(cr.offset() == 25196L) {
//            logger.info("Behandler sedSendt offset: ${cr.offset()}")
//            consumeSedSendt(hendelse, cr, acknowledgment)
//        } else {
//            acknowledgment.acknowledge()
//        }
//    }

//    @KafkaListener(groupId = "\${kafka.sedMottatt.groupid}-recovery",
//            topicPartitions = [TopicPartition(topic = "\${kafka.sedMottatt.topic}",
//                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "38518")])])
//    fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
//        if(cr.offset() == 38518L) {
//            logger.info("Behandler sedMottatt offset: ${cr.offset()}")
//            consumeSedSendt(hendelse, cr, acknowledgment)
//        } else {
//            acknowledgment.acknowledge()
//        }
//    }
}

internal class JournalforingException(cause: Throwable) : RuntimeException(cause)
