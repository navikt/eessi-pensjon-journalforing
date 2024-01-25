package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
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
class SedMottattListener (
    private val journalforingService: JournalforingService,
    private val personidentifiseringService: PersonidentifiseringService,
    private val euxService: EuxService,
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) : SedListenerBase(fagmodulService, bestemSakService) {

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

                val offsetToSkip = listOf(524914L, 530474L, 549326L, 549343L, 564697L, 573162L, 580192L, 592980L, 748455L, 748872L, 794071L, 814894L, 814914L, 830049L, 830051L)
                try {
                    val offset = cr.offset()
                    if (offset in offsetToSkip) {
                        logger.warn("Hopper over offset: $offset grunnet feil ved henting av vedlegg...")
                    } else {

                        val sedHendelse = SedHendelse.fromJson(hendelse)

//

                        if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                            acknowledgment.acknowledge()
                            return@measure
                        }
                        logger.info("***Innkommet sed, uten navbruker: ${mapAnyToJsonWithoutSensitiveData(sedHendelse, listOf("navBruker"))}")

                        if (GyldigeHendelser.mottatt(sedHendelse)) {
                            val bucType = sedHendelse.bucType!!
                            val buc = euxService.hentBuc(sedHendelse.rinaSakId)

                            logger.info("*** Starter innkommende journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")

                            val alleGyldigeDokumenter = euxService.hentAlleGyldigeDokumenter(buc)
                            val alleSedMedGyldigStatus = euxService.hentSedMedGyldigStatus(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val kansellerteSeder = euxService.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)

                            val harAdressebeskyttelse = personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedMedGyldigStatus)

                            //identifisere Person hent Person fra PDL valider Person
                            val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(alleSedMedGyldigStatus, bucType)
                            val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleSedMedGyldigStatus, bucType, potensiellePersonRelasjoner, MOTTATT, sedHendelse.rinaSakId)

                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                                bucType,
                                sedHendelse.sedType,
                                MOTTATT,
                                sedHendelse.rinaDokumentId,
                                identifisertePersoner,
                                potensiellePersonRelasjoner
                            )

                            val alleSedIBucList = alleSedMedGyldigStatus.flatMap { (_, sed) -> listOf(sed) }
                            val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBucList, kansellerteSeder)
                            val saktypeFraSed = euxService.hentSaktypeType(sedHendelse, alleSedIBucList).takeIf {bucType == P_BUC_10 || bucType  == R_BUC_02 }
                            val sakInformasjon = pensjonSakInformasjon(identifisertPerson, bucType, saktypeFraSed, alleSedIBucList)
                            val saktype = populerSaktype(saktypeFraSed, sakInformasjon, bucType)

                            val currentSed =
                                alleSedMedGyldigStatus.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second
                            journalforingService.journalfor(
                                sedHendelse,
                                MOTTATT,
                                identifisertPerson,
                                fdato,
                                saktype,
                                sakInformasjon,
                                currentSed,
                                harAdressebeskyttelse,
                                identifisertePersoner.count(),
                                gjennySakId = null
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
}

internal class SedMottattRuntimeException(cause: Throwable) : RuntimeException(cause)