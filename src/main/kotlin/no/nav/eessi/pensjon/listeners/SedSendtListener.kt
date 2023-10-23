package no.nav.eessi.pensjon.listeners

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.navansatt.NavansattKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
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
class SedSendtListener(
    private val journalforingService: JournalforingService,
    private val personidentifiseringService: PersonidentifiseringService,
    private val dokumentHelper: EuxService,
    private val fagmodulService: FagmodulService,
    private val bestemSakService: BestemSakService,
    private val navansattKlient: NavansattKlient,
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
                val offsetToSkip = listOf<Long>(133722, 143447, 176379, 183457, 183585)
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
                            val buc = dokumentHelper.hentBuc(sedHendelse.rinaSakId)

                            navAnsatt(buc, sedHendelse)

                            logger.info("*** Starter utgående journalføring for SED: ${sedHendelse.sedType}, BucType: $bucType, RinaSakID: ${sedHendelse.rinaSakId} ***")

                            val alleGyldigeDokumenter = dokumentHelper.hentAlleGyldigeDokumenter(buc)
                            val alleSedIBucPair = dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
                            val harAdressebeskyttelse = personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(alleSedIBucPair)
                            val kansellerteSeder = dokumentHelper.hentAlleKansellerteSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)

                            //identifisere Person hent Person fra PDL valider Person
                            val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(alleSedIBucPair, bucType)
                            val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleSedIBucPair, bucType, potensiellePersonRelasjoner, SENDT, sedHendelse.rinaSakId)

                            val identifisertPerson = personidentifiseringService.hentIdentifisertPerson(
                                bucType,
                                sedHendelse.sedType,
                                SENDT,
                                sedHendelse.rinaDokumentId,
                                identifisertePersoner,
                                potensiellePersonRelasjoner
                            )

                            val alleSedIBucList = alleSedIBucPair.flatMap { (_, sed) -> listOf(sed) }
                            val fdato = personidentifiseringService.hentFodselsDato(identifisertPerson, alleSedIBucList, kansellerteSeder)
                            val pesysSakId = fagmodulService.hentSakIdFraSED(alleSedIBucList)

                            if (identifisertPerson == null && !pesysSakId.isNullOrEmpty())
                                journalforingService.journalforUkjentPersonKjentPersysSakId(sedHendelse, SENDT, fdato, null, pesysSakId)
                            else {
                                val sakTypeFraSED = dokumentHelper.hentSaktypeType(sedHendelse, alleSedIBucList).takeIf { bucType == P_BUC_10 || bucType == R_BUC_02 }
                                val sakInformasjon = pensjonSakInformasjonSendt(identifisertPerson, bucType, sakTypeFraSED, alleSedIBucList)
                                val saktype = populerSaktype(sakTypeFraSED, sakInformasjon, sedHendelse)
                                val currentSed = alleSedIBucPair.firstOrNull { it.first == sedHendelse.rinaDokumentId }?.second

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
                                        .also { logger.info("Antall identifisertePersoner: $it") }
                                )
                            }
                        }
                        acknowledgment.acknowledge()
                        logger.info("Acket sedSendt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    }
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av sendt SED-hendelse:\\n ${hendelse.replaceAfter("navBruker", "******")}", ex)
                    throw SedSendtRuntimeException(ex)
                }
                latch.countDown()
            }
        }
    }

    /**
     * Velger saktype fra enten bestemSak eller pensjonsinformasjon der det foreligger.
     */
    private fun pensjonSakInformasjonSendt(identifisertPerson: IdentifisertPerson?, bucType: BucType, saktypeFraSed: SakType?, alleSedIBuc: List<SED>): SakInformasjon? {
        logger.info("skal hente pensjonsak med bruk av bestemSak")

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

    private fun populerSaktype(saktypeFraSED: SakType?, sakInformasjon: SakInformasjon?, sedHendelseModel: SedHendelse): SakType? {
        if (sedHendelseModel.bucType == P_BUC_02 && sakInformasjon != null && sakInformasjon.sakType == UFOREP && sakInformasjon.sakStatus == AVSLUTTET) return null
        else if (sedHendelseModel.bucType == P_BUC_10 && saktypeFraSED == GJENLEV) return sakInformasjon?.sakType ?: saktypeFraSED
        else if (saktypeFraSED != null) return saktypeFraSED
        return sakInformasjon?.sakType
    }

    fun navAnsatt(buc: Buc, sedHendelse: SedHendelse) : String?  {
        val navAnsatt = buc.documents?.firstOrNull{it.id == sedHendelse.rinaDokumentId }?.versions?.last()?.user?.name
        logger.debug("navAnsatt: $navAnsatt")
        if (navAnsatt == null) {
            logger.warn("Fant ingen NAV_ANSATT i BUC: ${buc.processDefinitionName} med sakId: ${buc.id}")
        } else {
            logger.info("Nav ansatt i ${buc.processDefinitionName} med sakId ${buc.id} er: $navAnsatt")
//            navansattKlient.hentAnsatt(navAnsatt).also { logger.info("hentNavAnsatt: $it") }
            return navansattKlient.hentAnsattEnhet(navAnsatt).also { logger.info("NavAnsatt enhet: $it") }
        }
        return ""
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
}

internal class SedSendtRuntimeException(cause: Throwable) : RuntimeException(cause)