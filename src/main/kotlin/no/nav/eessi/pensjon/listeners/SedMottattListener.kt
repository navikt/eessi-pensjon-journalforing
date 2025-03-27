package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.OMSORG
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
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

    private val offsetsToSkip = listOf(1403596L, 1421358L, 1421595L, 1433863L, 1433889L, 1434851L, 1558391)


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

    override fun behandleSedHendelse(sedHendelse: SedHendelse, buc: Buc) {

        val bucType = sedHendelse.bucType!!

        logger.info("*** Starter innkommende journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId}, SedID: ${sedHendelse.sedId} ***")

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

        val identifisertPersonBirthDate = identifisertPerson?.fnr?.getAge() ?: if(fdato != null) Period.between(fdato, LocalDate.now()).years else null
        if ((identifisertPersonBirthDate != null && identifisertPersonBirthDate < 67)) {
            if (bucType == P_BUC_02 && sedHendelse.sedType == SedType.P2100) {
                logger.info("Innkommende P_BUC_02 med sedType ${sedHendelse.sedType} blir behandlet som GjennySak")
                val gjennyTema = if (Period.between(fdato, LocalDate.now()).years > 19) OMSORG else BARNEP
                gcpStorageService.lagre(sedHendelse.rinaSakId, GjennySak(null, gjennyTema.name))
            }
        }

        val saksInfoSamlet = hentSaksInformasjonForEessi(
            alleSedIBucList,
            sedHendelse,
            bucType,
            identifisertPerson,
            MOTTATT,
            null
            // trenger ikke å sende med currentSed for MOTTATT, da det dette kommer fra utlandet
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