package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
        private val bestemSakService: BestemSakService,
        @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
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
                if(cr.offset() == 0L && profile == "prod") {
                    logger.error("Applikasjonen har forsøkt å prosessere sedSendt meldinger fra offset 0, stopper prosessering")
                    throw RuntimeException("Applikasjonen har forsøkt å prosessere sedSendt meldinger fra offset 0, stopper prosessering")
                }
                logger.debug(hendelse)
                try {
                    val offset = cr.offset()

                    val sedHendelse = SedHendelseModel.fromJson(hendelse)
                    if (GyldigeHendelser.sendt(sedHendelse)) {
                        val bucType = sedHendelse.bucType!!

                        logger.info("*** Starter utgående journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")
                        val alleGyldigeDokumenter = sedDokumentHelper.hentAlleGydligeDokumenter(sedHendelse.rinaSakId)
                        val alleSedIBuc = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                        val kansellerteSeder = sedDokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                        val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(sedHendelse.navBruker, alleSedIBuc, bucType, sedHendelse.sedType)
                        val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBuc, kansellerteSeder)
                        val sakTypeFraSED = sedDokumentHelper.hentSaktypeType(sedHendelse, alleSedIBuc)
                        val sakInformasjon = pensjonSakInformasjonSendt(identifisertPerson, bucType, sakTypeFraSED, alleSedIBuc)
                        val saktype = populerSaktype(sakTypeFraSED, sakInformasjon, sedHendelse, SENDT)

                        journalforingService.journalfor(sedHendelse, SENDT, identifisertPerson, fdato, saktype, offset, sakInformasjon)
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
                if(cr.offset() == 0L && profile == "prod") {
                    logger.error("Applikasjonen har forsøkt å prosessere sedMottatt meldinger fra offset 0, stopper prosessering")
                    throw RuntimeException("Applikasjonen har forsøkt å prosessere sedMottatt meldinger fra offset 0, stopper prosessering")
                }
                logger.debug(hendelse)

                try {
                    val offset = cr.offset()
                    if (offset == 38518L) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {
                        logger.info("*** Offset $offset  Partition ${cr.partition()} ***")
                        val sedHendelse = SedHendelseModel.fromJson(hendelse)
                        if (GyldigeHendelser.mottatt(sedHendelse)) {
                            val bucType = sedHendelse.bucType!!

                            logger.info("*** Starter innkommende journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")
                            val alleGyldigeDokumenter = sedDokumentHelper.hentAlleGydligeDokumenter(sedHendelse.rinaSakId)
                            val alleSedIBuc = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val kansellerteSeder = sedDokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(sedHendelse.navBruker, alleSedIBuc, bucType, sedHendelse.sedType)
                            val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBuc, kansellerteSeder)
                            val saktypeFraSed = sedDokumentHelper.hentSaktypeType(sedHendelse, alleSedIBuc)
                            val sakInformasjon = pensjonSakInformasjonMottatt(identifisertPerson, sedHendelse)
                            val saktype = populerSaktype(saktypeFraSed, sakInformasjon, sedHendelse, HendelseType.MOTTATT)

                            journalforingService.journalfor(sedHendelse, HendelseType.MOTTATT, identifisertPerson, fdato, saktype, offset, sakInformasjon)
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

    private fun pensjonSakInformasjonMottatt(identifisertPerson: IdentifisertPerson?, sedHendelseModel: SedHendelseModel): SakInformasjon? {
        if (identifisertPerson?.aktoerId == null) return null

        return when(sedHendelseModel.bucType) {
            BucType.P_BUC_02 -> bestemSakService.hentSakInformasjon(identifisertPerson.aktoerId, sedHendelseModel.bucType, identifisertPerson.personRelasjon.saktype)
            BucType.P_BUC_03 -> bestemSakService.hentSakInformasjon(identifisertPerson.aktoerId, sedHendelseModel.bucType)
            else  -> null
        }
    }

    private fun pensjonSakInformasjonSendt(identifisertPerson: IdentifisertPerson?, bucType: BucType, ytelsestypeFraSed: Saktype?, alleSedIBuc: List<SED>): SakInformasjon? {
        if (identifisertPerson?.aktoerId == null) return null

        val aktoerId = identifisertPerson.aktoerId
        val sakInformasjonFraBestemSak = bestemSakService.hentSakInformasjon(aktoerId, bucType, bestemSaktypeFraSed(ytelsestypeFraSed, identifisertPerson, bucType))

        return if (sakInformasjonFraBestemSak == null && bucType == BucType.P_BUC_05 || sakInformasjonFraBestemSak == null && bucType == BucType.P_BUC_10) {
            logger.info("skal hente pensjonSak for sed kap.1 og validere mot pesys")
            sedDokumentHelper.hentPensjonSakFraSED(aktoerId, alleSedIBuc)
        } else
            sakInformasjonFraBestemSak
    }

    private fun bestemSaktypeFraSed(saktypeFraSed: Saktype?, identifisertPerson: IdentifisertPerson?, bucType: BucType): Saktype? {
        val saktype = identifisertPerson?.personRelasjon?.saktype
        logger.debug("populerSaktypeFraSed: fraSED $saktypeFraSed  identPersonYtelse: $saktype")
        if (bucType == BucType.P_BUC_10 && saktypeFraSed == Saktype.GJENLEV) {
            return saktype
        }
        return saktypeFraSed ?: saktype
    }

    private fun populerSaktype(saktypeFraSED: Saktype?, sakInformasjon: SakInformasjon?, sedHendelseModel: SedHendelseModel, hendelseType: HendelseType): Saktype? {
        if (sedHendelseModel.bucType == BucType.P_BUC_02 && hendelseType == SENDT && sakInformasjon != null && sakInformasjon.sakType == Saktype.UFOREP && sakInformasjon.sakStatus == SakStatus.AVSLUTTET) {
            return null
        } else if (sedHendelseModel.bucType == BucType.P_BUC_10 && saktypeFraSED == Saktype.GJENLEV) {
            return sakInformasjon?.sakType ?: saktypeFraSED
        } else if (saktypeFraSED != null) {
            return saktypeFraSED
        }
        return sakInformasjon?.sakType
    }



    /**
     * Ikke slett funksjonene under før vi har et bedre opplegg for tilbakestilling av topic.
     * Se jira-sak: EP-968
     **/

    /*
    @KafkaListener(groupId = "\${kafka.sedSendt.groupid}-recovery",
            topicPartitions = [TopicPartition(topic = "\${kafka.sedSendt.topic}",
                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "25196")])])
    fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        if (cr.offset() == 25196L) {
            logger.info("Behandler sedSendt offset: ${cr.offset()}")
            consumeSedSendt(hendelse, cr, acknowledgment)
        } else {
            acknowledgment.acknowledge()
        }
    }

    @KafkaListener(groupId = "\${kafka.sedMottatt.groupid}-recovery",
            topicPartitions = [TopicPartition(topic = "\${kafka.sedMottatt.topic}",
                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "104259")])])
    fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        if (cr.offset() == 104259L) {
            logger.info("Behandler sedMottatt offset: ${cr.offset()}")
            consumeSedMottatt(hendelse, cr, acknowledgment)
        } else {
            throw java.lang.RuntimeException()
        }
    }
*/

}

internal class JournalforingException(cause: Throwable) : RuntimeException(cause)
