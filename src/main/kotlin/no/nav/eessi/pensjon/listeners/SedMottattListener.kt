package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
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
class SedMottattListener(
    private val journalforingService: JournalforingService,
    private val personidentifiseringService: PersonidentifiseringService,
    private val dokumentHelper: EuxService,
    private val bestemSakService: BestemSakService,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedMottattListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    fun getLatch() = latch

    @PostConstruct
    fun initMetrics() {
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        idIsGroup = false,
        topics = ["\${kafka.sedMottatt.topic}"],
        groupId = "\${kafka.sedMottatt.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeIncomingSed.measure {

                logger.info("Innkommet sedMottatt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")

                //Forsøker med denne en gang til 258088L
                try {
                    logger.info("*** Offset ${cr.offset()}  Partition ${cr.partition()} ***")
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)

                    if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                        logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                        acknowledgment.acknowledge()
                        return@measure
                    }

                    if (GyldigeHendelser.mottatt(sedHendelse)) {
                        val bucType = sedHendelse.bucType!!

                        logger.info("*** Starter innkommende journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")
                        val buc = dokumentHelper.hentBuc(sedHendelse.rinaSakId)
                        val erNavCaseOwner = dokumentHelper.isNavCaseOwner(buc)
                        val alleGyldigeDokumenter = dokumentHelper.hentAlleGyldigeDokumenter(buc)

                        val alleSedIBucPair = dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                        val kansellerteSeder = dokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)

                        val harAdressebeskyttelse = personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedIBucPair)

                        //identifisere Person hent Person fra PDL valider Person
                        val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                            alleSedIBucPair, bucType, sedHendelse.sedType, HendelseType.MOTTATT, sedHendelse.rinaDokumentId, erNavCaseOwner
                        )

                        val alleSedIBucList = alleSedIBucPair.flatMap{ (_, sed) -> listOf(sed) }
                        val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBucList, kansellerteSeder)
                        val saktypeFraSed = dokumentHelper.hentSaktypeType(sedHendelse, alleSedIBucList)
                        val sakInformasjon = pensjonSakInformasjonMottatt(identifisertPerson, sedHendelse)
                        val saktype = populerSaktype(saktypeFraSed, sakInformasjon, sedHendelse, HendelseType.MOTTATT)

                        val currentSed = alleSedIBucPair.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second
                        journalforingService.journalfor(
                            sedHendelse,
                            HendelseType.MOTTATT,
                            identifisertPerson,
                            fdato,
                            saktype,
                            cr.offset(),
                            sakInformasjon,
                            currentSed,
                            harAdressebeskyttelse,
                        )

                    }

                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av mottatt SED-hendelse:\n ${trimFnrString(hendelse)} \n", ex)
                    throw SedMottattRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    private fun pensjonSakInformasjonMottatt(identifisertPerson: IdentifisertPerson?, sedHendelseModel: SedHendelseModel): SakInformasjon? {
        if (identifisertPerson?.aktoerId == null) return null

        return when(sedHendelseModel.bucType) {
            P_BUC_01 -> bestemSakService.hentSakInformasjon(identifisertPerson.aktoerId, sedHendelseModel.bucType)
            P_BUC_02 -> bestemSakService.hentSakInformasjon(identifisertPerson.aktoerId, sedHendelseModel.bucType, identifisertPerson.personRelasjon.saktype)
            P_BUC_03 -> bestemSakService.hentSakInformasjon(identifisertPerson.aktoerId, sedHendelseModel.bucType)
            else  -> null
        }
    }

    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, sedHendelseModel: SedHendelseModel, hendelseType: HendelseType): SakType? {
        if (sedHendelseModel.bucType == P_BUC_02 && hendelseType == HendelseType.SENDT && sakInformasjon != null && sakInformasjon.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET) {
            return null
        } else if (sedHendelseModel.bucType == P_BUC_10 && saktypeFraSED == GJENLEV) {
            return sakInformasjon?.sakType ?: saktypeFraSED
        } else if (saktypeFraSED != null) {
            return saktypeFraSED
        }
        return sakInformasjon?.sakType
    }

    private fun trimFnrString(fnrAsString: String) = fnrAsString.replace("[^0-9]".toRegex(), "")



    /**
     * Ikke slett funksjonene under før vi har et bedre opplegg for tilbakestilling av topic.
     * Se jira-sak: EP-968
     **/
    /*
   @KafkaListener(
           containerFactory = "sedKafkaListenerContainerFactory",
           groupId = "\${kafka.sedMottatt.groupid}-recovery",
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

internal class SedMottattRuntimeException(cause: Throwable) : RuntimeException(cause)