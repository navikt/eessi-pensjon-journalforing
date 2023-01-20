package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
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
class SedSendtListener(
    private val journalforingService: JournalforingService,
    private val personidentifiseringService: PersonidentifiseringService,
    private val dokumentHelper: EuxService,
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedSendtListener::class.java)
    private val latch = CountDownLatch(1)
    private lateinit var consumeOutgoingSed: MetricsHelper.Metric

    fun getLatch() = latch

    @PostConstruct
    fun initMetrics() {
        consumeOutgoingSed = metricsHelper.init("consumeOutgoingSed")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.sedSendt.topic}"],
        groupId = "\${kafka.sedSendt.groupid}"
    )
    fun consumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeOutgoingSed.measure {
                logger.info("Innkommet sedSendt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                val offsetToSkip = emptyList<Long>()
                try {
                    val offset = cr.offset()
                    if (offset in offsetToSkip) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {

                        val sedHendelse = SedHendelseModel.fromJson(hendelse)
                        if (GyldigeHendelser.sendt(sedHendelse)) {
                            val bucType = sedHendelse.bucType!!

                            logger.info("*** Starter utgående journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")
                            val buc = dokumentHelper.hentBuc(sedHendelse.rinaSakId)
                            val erNavCaseOwner = dokumentHelper.isNavCaseOwner(buc)
                            val alleGyldigeDokumenter = dokumentHelper.hentAlleGyldigeDokumenter(buc)
                            val alleSedIBucPair =
                                dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val harAdressebeskyttelse =
                                personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedIBucPair)

                            val kansellerteSeder =
                                dokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                                alleSedIBucPair,
                                bucType,
                                sedHendelse.sedType,
                                HendelseType.SENDT,
                                sedHendelse.rinaDokumentId,
                                erNavCaseOwner
                            )

                            val alleSedIBucList = alleSedIBucPair.flatMap { (_, sed) -> listOf(sed) }
                            val fdato = personidentifiseringService.hentFodselsDato(
                                identifisertPerson,
                                alleSedIBucList,
                                kansellerteSeder
                            )
                            val sakTypeFraSED = dokumentHelper.hentSaktypeType(sedHendelse, alleSedIBucList)
                            val sakInformasjon =
                                pensjonSakInformasjonSendt(identifisertPerson, bucType, sakTypeFraSED, alleSedIBucList)
                            val saktype = populerSaktype(sakTypeFraSED, sakInformasjon, sedHendelse, HendelseType.SENDT)
                            val currentSed =
                                alleSedIBucPair.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second

                            journalforingService.journalfor(
                                sedHendelse, HendelseType.SENDT,
                                identifisertPerson,
                                fdato,
                                saktype,
                                offset,
                                sakInformasjon,
                                currentSed,
                                harAdressebeskyttelse
                            )
                        }
                        acknowledgment.acknowledge()
                        logger.info("Acket sedSendt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                        logger.info("Genererer automatiseringstatistikk")
                    }


                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av sendt SED-hendelse:\n $hendelse \n", ex)
                    throw SedSendtRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    private fun pensjonSakInformasjonSendt(identifisertPerson: IdentifisertPerson?, bucType: BucType, ytelsestypeFraSed: SakType?, alleSedIBuc: List<SED>): SakInformasjon? {
        if (identifisertPerson?.aktoerId == null) return null

        logger.info("skal hente pensjonsak med bruk av bestemSak")
        val aktoerId = identifisertPerson.aktoerId
        val sakInformasjonFraBestemSak = bestemSakService.hentSakInformasjon(aktoerId, bucType, bestemSaktypeFraSed(ytelsestypeFraSed, identifisertPerson, bucType))

        logger.info("skal hente pensjonSak for SED kap.1 og validere mot pesys")
        val sakInformasjonFraSed = fagmodulService.hentPensjonSakFraSED(aktoerId, alleSedIBuc)

        return when {
            sakInformasjonFraSed != null && sakInformasjonFraBestemSak == null -> sakInformasjonFraSedMedLogging(sakInformasjonFraSed)
            sakInformasjonFraSed == null && sakInformasjonFraBestemSak != null -> sakInformasjonFraBestemSakMedLogging(sakInformasjonFraBestemSak)
            sakInformasjonFraSed?.sakId != sakInformasjonFraBestemSak?.sakId -> sakInformasjonFraSedUlikBestemSakMedLogging(sakInformasjonFraSed)
            else -> sakInformasjonBestemsakMedLogging(sakInformasjonFraBestemSak)
        }

    }

    private fun sakInformasjonFraSedMedLogging(sakfrased: SakInformasjon?): SakInformasjon? {
        logger.info("Sakid fra bestemSak er null, velger sakid fra sed. sakid: ${sakfrased?.sakId}")
        return sakfrased
    }

    private fun sakInformasjonFraBestemSakMedLogging(bestemsak: SakInformasjon?): SakInformasjon? {
        logger.info("Sakid fra sed er null, velger sakid fra bestemsak, sakid: ${bestemsak?.sakId}")
        return bestemsak
    }

    private fun sakInformasjonFraSedUlikBestemSakMedLogging(sakfrased: SakInformasjon?): SakInformasjon? {
        logger.info("Sakid fra sed ulik sakid fra bestemSak, velger sakid fra sed, sakid: ${sakfrased?.sakId}")
        return sakfrased
    }

    private fun sakInformasjonBestemsakMedLogging(bestemsak: SakInformasjon?): SakInformasjon? {
        logger.info("Velger sakid fra bestemSak, sakid: ${bestemsak?.sakId}")
        return bestemsak
    }


    private fun bestemSaktypeFraSed(
        saktypeFraSed: SakType?,
        identifisertPerson: IdentifisertPerson?,
        bucType: BucType
    ): SakType? {
        val saktype = identifisertPerson?.personRelasjon?.saktype
        logger.debug("populerSaktypeFraSed: fraSED $saktypeFraSed  identPersonYtelse: $saktype")
        if (bucType == P_BUC_10 && saktypeFraSed == GJENLEV) {
            return saktype
        }
        return saktypeFraSed ?: saktype
    }

    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, sedHendelseModel: SedHendelseModel, hendelseType: HendelseType): SakType? {
        if (sedHendelseModel.bucType == P_BUC_02 && hendelseType == HendelseType.SENDT && sakInformasjon != null && sakInformasjon.sakType == UFOREP && sakInformasjon.sakStatus == SakStatus.AVSLUTTET) {
            return null
        } else if (sedHendelseModel.bucType == P_BUC_10 && saktypeFraSED == GJENLEV) {
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
//    @KafkaListener(
//        containerFactory = "sedKafkaListenerContainerFactory",
//        groupId = "\${kafka.sedSendt.groupid}-recovery",
//        topicPartitions = [TopicPartition(topic = "\${kafka.sedSendt.topic}",
//                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "70196")])])
//    fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
//        if (cr.offset() in listOf(70196L, 70197L, 70768L)) {
//            logger.info("Behandler sedSendt offset: ${cr.offset()}")
//            consumeSedSendt(hendelse, cr, acknowledgment)
//        }
//    }
}

internal class SedSendtRuntimeException(cause: Throwable) : RuntimeException(cause)