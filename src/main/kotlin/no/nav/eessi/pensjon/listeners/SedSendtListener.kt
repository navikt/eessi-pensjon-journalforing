package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.navansatt.NavansattKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
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
class SedSendtListener(
    private val journalforingService: JournalforingService,
    private val personidentifiseringService: PersonidentifiseringService,
    private val euxService: EuxService,
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    private val navansattKlient: NavansattKlient,
    private val gcpStorageService: GcpStorageService,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) : SedListenerBase(fagmodulService, bestemSakService) {

    private val logger = LoggerFactory.getLogger(SedSendtListener::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private val latch = CountDownLatch(1)
    private lateinit var consumeOutgoingSed: MetricsHelper.Metric

    fun getLatch() = latch

    init {
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
                val offsetToSkip = listOf<Long>(133722, 143447, 176379, 183457, 183585, 204028)
                try {
                    val offset = cr.offset()
                    if (offset in offsetToSkip) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {

                        val sedHendelse = SedHendelse.fromJson(hendelse)

                        if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                            acknowledgment.acknowledge()
                            return@measure
                        }
                        if (GyldigeHendelser.sendt(sedHendelse)) {
                            val bucType = sedHendelse.bucType!!
                            val buc = euxService.hentBuc(sedHendelse.rinaSakId)

                            val navAnsattMedEnhet = navansattKlient.navAnsattMedEnhetsInfo(buc, sedHendelse)


                            logger.info("*** Starter utgående journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")

                            val alleSedMedGyldigStatus = euxService.hentSedMedGyldigStatus(sedHendelse.rinaSakId, buc)
                            val harAdressebeskyttelse = personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedMedGyldigStatus)
                            val kansellerteSeder = euxService.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, buc)

                            //identifisere Person hent Person fra PDL valider Person
                            val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(alleSedMedGyldigStatus, bucType)
                            val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(potensiellePersonRelasjoner)

                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                                bucType,
                                sedHendelse.sedType,
                                SENDT,
                                sedHendelse.rinaDokumentId,
                                identifisertePersoner,
                                potensiellePersonRelasjoner
                            )

                            val alleSedIBucList = alleSedMedGyldigStatus.flatMap { (_, sed) -> listOf(sed) }
                            val fdato = personidentifiseringService.hentFodselsDato(
                                identifisertPerson,
                                alleSedIBucList,
                                kansellerteSeder
                            )
                            val saksIdFraSed = fagmodulService.hentSakIdFraSED(alleSedIBucList)

                            if (identifisertPerson == null && !saksIdFraSed.isNullOrEmpty())
                                journalforingService.journalforUkjentPersonKjentPersysSakId(
                                    sedHendelse,
                                    SENDT,
                                    fdato,
                                    null,
                                    saksIdFraSed
                                )
                            else {
                                val sakTypeFraSED = euxService.hentSaktypeType(sedHendelse, alleSedIBucList).takeIf { bucType == P_BUC_10 || bucType == R_BUC_02 }
                                val gjennySak = gcpStorageService.eksisterer(sedHendelse.rinaSakId)
                                val sakInformasjon = if (gjennySak) null else {
                                    pensjonSakInformasjon(
                                        identifisertPerson,
                                        bucType,
                                        sakTypeFraSED,
                                        alleSedIBucList
                                    )
                                }
                                val saktype = populerSaktype(sakTypeFraSED, sakInformasjon, bucType)
                                val currentSed =
                                    alleSedMedGyldigStatus.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second

                                journalforingService.journalfor(
                                    sedHendelse,
                                    SENDT,
                                    identifisertPerson,
                                    fdato,
                                    saktype,
                                    sakInformasjon,
                                    currentSed,
                                    harAdressebeskyttelse,
                                    identifisertePersoner.count()
                                        .also { logger.info("Antall identifisertePersoner: $it") },
                                    navAnsattMedEnhet,
                                    saksIdFraSed
                                )
                            }
                        }
                        acknowledgment.acknowledge()
                        logger.info("Acket sedSendt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    }
                } catch (ex: Exception) {
                    logger.error(
                        "Noe gikk galt under behandling av sendt SED-hendelse:\\n ${
                            hendelse.replaceAfter(
                                "navBruker",
                                "******"
                            )
                        }", ex
                    )
                    throw SedSendtRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }
}

    /**
     * Ikke slett funksjonene under før vi har et bedre opplegg for tilbakestilling av topic.
     * Se jira-sak: EP-968
     **/
//    @KafkaListener(
//        containerFactory = "sedKafkaListenerContainerFactory",
//        groupId = "\${kafka.sedSendt.groupid}-recovery",
//        topicPartitions = [TopicPartition(topic = "\${kafka.sedSendt.topic}",
//                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "182678")])])
//    fun recoverConsumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
//        if (cr.offset() in listOf(182678L, 182701L, 183457L, 183585L)) {
//            logger.info("Behandler sedSendt offset: ${cr.offset()}")
//            consumeSedSendt(hendelse, cr, acknowledgment)
//        }
//    }
//}

internal class SedSendtRuntimeException(cause: Throwable) : RuntimeException(cause)