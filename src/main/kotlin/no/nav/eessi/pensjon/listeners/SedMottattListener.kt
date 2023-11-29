package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.mapAnyToJsonWithoutSensitiveData
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
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedMottattListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    fun getLatch() = latch

    init {
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

                val offsetToSkip = listOf(524914L, 530474L, 549326L, 549343L, 564697L, 573162L, 580192L, 592980L, 748455L, 748872L, 794071L, 814894L, 814914L)
                try {
                    val offset = cr.offset()
                    if (offset in offsetToSkip) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {

                        val sedHendelse = SedHendelse.fromJson(hendelse)

                        if(sedHendelse.rinaSakId in listOf("4179656")){
                            logger.error("Hopper over denne saken grunnet feil ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                            acknowledgment.acknowledge()
                            return@measure
                        }

                        if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                            acknowledgment.acknowledge()
                            return@measure
                        }
                        logger.info("***Innkommet sed, uten navbruker: ${mapAnyToJsonWithoutSensitiveData(sedHendelse, listOf("navBruker"))}")

                        if (GyldigeHendelser.mottatt(sedHendelse)) {
                            val bucType = sedHendelse.bucType!!
                            val buc = dokumentHelper.hentBuc(sedHendelse.rinaSakId)

                            logger.info("*** Starter innkommende journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")

                            val alleGyldigeDokumenter = dokumentHelper.hentAlleGyldigeDokumenter(buc)
                            val alleSedIBucPair = dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val kansellerteSeder = dokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val harAdressebeskyttelse = personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedIBucPair)

                            //identifisere Person hent Person fra PDL valider Person
                            val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(alleSedIBucPair, bucType)
                            val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleSedIBucPair, bucType, potensiellePersonRelasjoner, MOTTATT, sedHendelse.rinaSakId)

                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                                bucType,
                                sedHendelse.sedType,
                                MOTTATT,
                                sedHendelse.rinaDokumentId,
                                identifisertePersoner,
                                potensiellePersonRelasjoner
                            )

                            val alleSedIBucList = alleSedIBucPair.flatMap { (_, sed) -> listOf(sed) }
                            val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBucList, kansellerteSeder)
                            val saktypeFraSed = dokumentHelper.hentSaktypeType(sedHendelse, alleSedIBucList).takeIf {bucType == P_BUC_10 || bucType  == R_BUC_02 }
                            val sakInformasjon = pensjonSakInformasjonMottatt(identifisertPerson, bucType, saktypeFraSed, alleSedIBucList)
                            val saktype = populerSaktype(saktypeFraSed, sakInformasjon, bucType)

                            val currentSed =
                                alleSedIBucPair.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second
                            journalforingService.journalfor(
                                sedHendelse,
                                MOTTATT,
                                identifisertPerson,
                                fdato,
                                saktype,
                                sakInformasjon,
                                currentSed,
                                harAdressebeskyttelse,
                                identifisertePersoner.count()
                            )

                        }
                        acknowledgment.acknowledge()
                        logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    }

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av mottatt SED-hendelse:\n ${hendelse.replaceAfter("navBruker", "******")}", ex)
                    throw SedMottattRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    private fun pensjonSakInformasjonMottatt(identifisertPerson: IdentifisertPerson?, bucType: BucType, saktypeFraSed: SakType?, alleSedIBuc: List<SED>): SakInformasjon? {

        val aktoerId = identifisertPerson?.aktoerId ?: return null
            .also { logger.info("IdentifisertPerson mangler aktørId. Ikke i stand til å hente ut saktype fra bestemsak eller pensjonsinformasjon") }

        fagmodulService.hentPensjonSakFraPesys(aktoerId, alleSedIBuc).let { pensjonsinformasjon ->
            if (pensjonsinformasjon?.sakType != null) {
                logger.info("Velger sakType ${pensjonsinformasjon.sakType} fra pensjonsinformasjon, for sakid: ${pensjonsinformasjon.sakId}")
                return pensjonsinformasjon
            }
        }
        bestemSakService.hentSakInformasjonViaBestemSak(aktoerId, bucType, saktypeFraSed, identifisertPerson).let {
            if (it?.sakType != null) {
                logger.info("Velger sakType ${it.sakType} fra bestemsak, for sak med sakid: ${it.sakId}")
                return it
            }
        }
        logger.info("Finner ingen sakType(fra bestemsak og pensjonsinformasjon) returnerer null.")
        return null
    }

    //TODO: Kan vi vurdere alle bucer som har mulighet for gjenlevende på samme måte som P_BUC_10 her?
    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, bucType: BucType): SakType? {
        if (bucType == P_BUC_02 && sakInformasjon != null && sakInformasjon.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET) return null
        else if (bucType == P_BUC_10 && saktypeFraSED == GJENLEV) return sakInformasjon?.sakType ?: saktypeFraSED
        else if (saktypeFraSED != null) return saktypeFraSED
        return sakInformasjon?.sakType
    }

    /**
     * Ikke slett funksjonene under før vi har et bedre opplegg for tilbakestilling av topic.
     * Se jira-sak: EP-968
     **/

//   @KafkaListener(
//           containerFactory = "sedKafkaListenerContainerFactory",
//           groupId = "\${kafka.sedMottatt.groupid}-recovery",
//           topicPartitions = [TopicPartition(topic = "\${kafka.sedMottatt.topic}",
//           partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "745823")])])
//   fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
//       if (cr.offset() in listOf<Long>(745823, 745854, 748455, 748872)){
//           logger.info("Behandler sedMottatt offset: ${cr.offset()}")
//           consumeSedMottatt(hendelse, cr, acknowledgment)
//       } else {
//           throw java.lang.RuntimeException()
//       }
//   }

}

internal class SedMottattRuntimeException(cause: Throwable) : RuntimeException(cause)