package no.nav.eessi.pensjon.listeners

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
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

                val offsetToSkip = listOf(524914L, 530474L, 549326L)
                try {
                    val offset = cr.offset()
                    if (offset in offsetToSkip) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {

                        logger.info("*** Offset ${cr.offset()}  Partition ${cr.partition()} ***")
                        val sedHendelse = SedHendelse.fromJson(hendelse)

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

                            val alleSedIBucPair =
                                dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val kansellerteSeder =
                                dokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)

                            val harAdressebeskyttelse =
                                personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedIBucPair)

                            //identifisere Person hent Person fra PDL valider Person
                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                                alleSedIBucPair,
                                bucType,
                                sedHendelse.sedType,
                                MOTTATT,
                                sedHendelse.rinaDokumentId,
                                erNavCaseOwner
                            )

                            val alleSedIBucList = alleSedIBucPair.flatMap { (_, sed) -> listOf(sed) }
                            val fdato = personidentifiseringService.hentFodselsDato(
                                identifisertPerson,
                                alleSedIBucList,
                                kansellerteSeder
                            )
                            val saktypeFraSed = dokumentHelper.hentSaktypeType(sedHendelse, alleSedIBucList)
                            val sakInformasjon = pensjonSakInformasjonMottatt(identifisertPerson, sedHendelse)
                            val saktype = populerSaktype(saktypeFraSed, sakInformasjon, sedHendelse, MOTTATT)

                            val currentSed =
                                alleSedIBucPair.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second
                            journalforingService.journalfor(
                                sedHendelse,
                                MOTTATT,
                                identifisertPerson,
                                fdato,
                                saktype,
                                cr.offset(),
                                sakInformasjon,
                                currentSed,
                                harAdressebeskyttelse,
                            )

                        }
                    }

                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av mottatt SED-hendelse:\n ${hendelse.replaceAfter("navBruker", "******")}", ex)
                    throw SedMottattRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    private fun pensjonSakInformasjonMottatt(identifisertPerson: IdentifisertPerson?, sedHendelse: SedHendelse): SakInformasjon? {
        if (identifisertPerson?.aktoerId == null) return null

        return when(sedHendelse.bucType) {
            P_BUC_01 -> bestemSakService.hentSakInformasjonViaBestemSak(identifisertPerson.aktoerId, P_BUC_01)
            P_BUC_02 -> bestemSakService.hentSakInformasjonViaBestemSak(identifisertPerson.aktoerId, P_BUC_02, identifisertPerson.personRelasjon?.saktype)
            P_BUC_03 -> bestemSakService.hentSakInformasjonViaBestemSak(identifisertPerson.aktoerId, P_BUC_03)
            else  -> null
        }
    }

    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, sedHendelseModel: SedHendelse, hendelseType: HendelseType): SakType? {
        if (sedHendelseModel.bucType == P_BUC_02 && hendelseType == SENDT && sakInformasjon != null && sakInformasjon.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET) {
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