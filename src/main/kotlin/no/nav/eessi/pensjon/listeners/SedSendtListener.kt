package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
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
) : SedListenerBase(fagmodulService, bestemSakService, gcpStorageService, euxService, profile) {

    private val logger = LoggerFactory.getLogger(SedSendtListener::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private val latch = CountDownLatch(1)
    private lateinit var consumeOutgoingSed: MetricsHelper.Metric

    fun getLatch() = latch

    init {
        consumeOutgoingSed = metricsHelper.init("consumeOutgoingSed")
    }
    private val offsetsToSkip = listOf<Long>(133722, 143447, 176379, 183457, 183585, 204028, 294886, 297871, 298929)

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.sedSendt.topic}"],
        groupId = "\${kafka.sedSendt.groupid}"
    )
    fun consumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeOutgoingSed.measure {
                logger.info("Innkommet sedSendt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")

                try {
                    if (!skippingOffsett(cr.offset(), offsetsToSkip)) {
                        behandleHendelse(hendelse, SENDT, acknowledgment)
                    }
                } catch (ex: Exception) {
                    logger.error("Feil ved behandling av SED-SENDT: ${hendelse.replaceAfter("navBruker","******")}", ex)
                    throw SedSendtRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    override fun behandleSedHendelse(sedHendelse: SedHendelse) {
        if (gcpStorageService.journalFinnes(sedHendelse.rinaSakId)) {
            logger.info("Utgående ${sedHendelse.sedType} med rinaId: ${sedHendelse.rinaSakId}  finnes i GCP storage")
        }
        val bucType = sedHendelse.bucType!!
        val buc = euxService.hentBuc(sedHendelse.rinaSakId)

        val navAnsattMedEnhet = navansattKlient.navAnsattMedEnhetsInfo(buc, sedHendelse)

        logger.info("*** Starter utgående journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***, SedId: ${sedHendelse.sedId} ***")

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
            alleSedIBucList.plus(kansellerteSeder)
        )

        val kravTypeISed = alleSedIBucList.firstOrNull { it.type == SedType.P15000 }?.nav?.krav?.type

        val saksInfoSamlet = hentSaksInformasjonForEessi(
            alleSedIBucList,
            sedHendelse,
            bucType,
            identifisertPerson,
            SENDT
        )

        val currentSed =  alleSedMedGyldigStatus.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            saksInfoSamlet,
            currentSed,
            harAdressebeskyttelse,
            identifisertePersoner.count()
                .also { logger.info("Antall identifisertePersoner: $it") },
            navAnsattMedEnhet,
            kravTypeISed
        )
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