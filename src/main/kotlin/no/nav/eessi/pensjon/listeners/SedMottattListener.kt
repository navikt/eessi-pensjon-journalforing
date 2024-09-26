package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Tema.EYBARNEP
import no.nav.eessi.pensjon.models.Tema.OMSTILLING
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
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
import java.time.LocalDate
import java.time.Period
import java.util.*
import java.util.concurrent.CountDownLatch

@Service
class SedMottattListener(
    private val journalforingService: JournalforingService,
    private val personidentifiseringService: PersonidentifiseringService,
    private val euxService: EuxService,
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    val gcpStorageService: GcpStorageService,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) : SedListenerBase(fagmodulService, bestemSakService, gcpStorageService, euxService, profile) {

    private val logger = LoggerFactory.getLogger(SedMottattListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    fun getLatch() = latch

    init {
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    private val offsetsToSkip = listOf(524914L, 530474L, 549326L, 549343L, 564697L, 573162L, 580192L, 592980L,
        748455L, 748872L, 794071L, 814894L, 814914L, 830049L, 830051L, 1266524L, 1280337L, 1280599L, 1280619L, 1280891L, 1280893L, 1281199L, 1282403L, 1283005L,1283448L, 1283478L, 1384455, 1403596L)

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

                try {
                    if (!skippingOffsett(cr.offset(), offsetsToSkip)) {
                        behandleHendelse(hendelse, MOTTATT, acknowledgment)
                    }
                } catch (ex: Exception) {
                    logger.error("Feil ved behandling av SED-MOTTATT: ${hendelse.replaceAfter("navBruker", "******")}", ex)
                    throw SedMottattRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    override fun behandleSedHendelse(sedHendelse: SedHendelse) {
        if (gcpStorageService.journalFinnes(sedHendelse.rinaSakId)) {
            logger.info("Innkommende ${sedHendelse.sedType} med rinaId: ${sedHendelse.rinaSakId}  finnes i GCP storage")
        }

        val bucType = sedHendelse.bucType!!
        val buc = euxService.hentBuc(sedHendelse.rinaSakId)

        logger.info("*** Starter innkommende journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")

        val alleSedMedGyldigStatus = euxService.hentSedMedGyldigStatus(sedHendelse.rinaSakId, buc)
        val kansellerteSeder = euxService.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, buc)

        val harAdressebeskyttelse =
            personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedMedGyldigStatus)

        //identifisere Person hent Person fra PDL valider Person
        val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(alleSedMedGyldigStatus, bucType)
        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(potensiellePersonRelasjoner)

        val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
            bucType,
            sedHendelse.sedType,
            MOTTATT,
            sedHendelse.rinaDokumentId,
            identifisertePersoner,
            potensiellePersonRelasjoner
        )

        val alleSedIBucList = alleSedMedGyldigStatus.flatMap { (_, sed) -> listOf(sed) }
        val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBucList.plus(kansellerteSeder))

        if (bucType == P_BUC_02 ) {
            val gjennyTema = if (Period.between(fdato, LocalDate.now()).years > 19) OMSORG else BARNEP
            gcpStorageService.lagre(sedHendelse.rinaSakId, GjennySak(null, gjennyTema.name))
        }

        val saksInfoSamlet = hentSaksInformasjonForEessi(
            alleSedIBucList,
            sedHendelse,
            bucType,
            identifisertPerson,
            MOTTATT
        )

        val currentSed = alleSedMedGyldigStatus.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second
        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            fdato,
            saksInfoSamlet,
            harAdressebeskyttelse,
            identifisertePersoner.count(),
            currentSed = currentSed,
        )
    }
}

internal class SedMottattRuntimeException(cause: Throwable) : RuntimeException(cause)