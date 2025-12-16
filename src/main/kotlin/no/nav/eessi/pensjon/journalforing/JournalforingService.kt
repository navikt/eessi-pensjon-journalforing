package no.nav.eessi.pensjon.journalforing

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.IdType.UTL_ORG
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OpprettOppgaveService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkMelding
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.exc.MismatchedInputException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

@Service
class JournalforingService(
    private val journalpostService: JournalpostService,
    private val oppgaveRoutingService: OppgaveRoutingService,
    private val kravInitialiseringsService: KravInitialiseringsService,
    private val statistikkPublisher: StatistikkPublisher,
    private val gcpStorageService: GcpStorageService,
    private val hentSakService: HentSakService,
    private val hentTemaService: HentTemaService,
    private val oppgaveService: OpprettOppgaveService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
    @Value("\${NAMESPACE}") private val env: String?
) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    private var journalforOgOpprettOppgaveForSed: MetricsHelper.Metric
    private var journalforOgOpprettOppgaveForSedMedUkjentPerson: MetricsHelper.Metric

    @Value("\${namespace}")
    lateinit var nameSpace: String

    init {
        journalforOgOpprettOppgaveForSed = metricsHelper.init("journalforOgOpprettOppgaveForSed")
        journalforOgOpprettOppgaveForSedMedUkjentPerson = metricsHelper.init("journalforOgOpprettOppgaveForSed")
    }

    /**
     * 1.) Henter dokumenter og vedlegg
     * 2.) Henter enhet
     * 3.) Oppretter journalpost
     * 4.) Ved utgpående mskinell journalføring -> oppdateres distribusjonsinfo
     * 5.) Dersom jf.post ikke blir ferdigstilt -> lage jf.oppgave
     * 6.) Hent oppgave-enhet
     * 7.) Ved automatisk jf, B_BUC_02, eller P_BUC_03 og mottatt ->
     *     a) lage BEHANDLE_SED oppgave
     *     b) og opprette krav automatisk
     * 8.) Generer statisikk melding
     */
    fun journalfor(
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        identifisertPerson: IdentifisertPerson?,
        fdato: LocalDate?,
        saksInfoSamlet: SaksInfoSamlet? = null,
        harAdressebeskyttelse: Boolean = false,
        identifisertePersoner: Int,
        navAnsattInfo: Pair<String, Enhet?>? = null,
        currentSed: SED?
    ) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                infologging(sedHendelse, hendelseType, saksInfoSamlet, identifisertPerson)
                secureLog.info("""Sed som skal journalføres: ${currentSed?.toJson()}""")

                val aktoerId = identifisertPerson?.aktoerId
                val alder = bestemAlder(identifisertPerson)
                val tema = hentTema(sedHendelse, alder, identifisertePersoner, saksInfoSamlet, currentSed)
                val tildeltJoarkEnhet = journalforingsEnhet(fdato, identifisertPerson, sedHendelse, hendelseType, saksInfoSamlet, harAdressebeskyttelse, identifisertePersoner, currentSed, tema)
                val institusjon = bestemAvsenderMottaker(hendelseType, sedHendelse)
                val arkivsaksnummer = hentArkivsaksnummer(sedHendelse, hendelseType, saksInfoSamlet, identifisertPerson)

                val journalpostRequest = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    identifisertPerson = identifisertPerson,
                    sedHendelseType = hendelseType,
                    tildeltJoarkEnhet = tildeltJoarkEnhet,
                    arkivsaksnummer = arkivsaksnummer,
                    saktype = saksInfoSamlet?.saktypeFraSed,
                    institusjon = institusjon,
                    identifisertePersoner = identifisertePersoner,
                    saksbehandlerInfo = navAnsattInfo,
                    tema = tema,
                    currentSed
                )
                sendJournalpostRequest(journalpostRequest, sedHendelse, hendelseType, navAnsattInfo, identifisertPerson, tildeltJoarkEnhet, aktoerId, tema, currentSed, saksInfoSamlet)
            } catch (ex: MismatchedInputException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Det oppstod en uventet feil ved journalforing av hendelse", ex)
                throw ex
            }
        }
    }

    private fun bestemAlder(identifisertPerson: IdentifisertPerson?): Int? {
        return if (identifisertPerson?.fnr?.erNpid == true || identifisertPerson?.fnr?.getBirthDate() == null) {
            identifisertPerson?.personRelasjon?.alder()
        } else {
            identifisertPerson.personRelasjon?.fnr?.getAge()
        }
    }

    private fun hentTema(
        sedHendelse: SedHendelse,
        alder: Int?,
        identifisertePersoner: Int,
        saksInfoSamlet: SaksInfoSamlet?,
        currentSed: SED?
    ): Tema {
        return hentTemaService.hentTema(sedHendelse, alder, identifisertePersoner, saksInfoSamlet, currentSed).also {
            logger.info("Hent tema gir: $it for ${sedHendelse.rinaSakId}, sedtype: ${sedHendelse.sedType}, buc: ${sedHendelse.bucType}")
        }
    }

    private fun bestemAvsenderMottaker(hendelseType: HendelseType, sedHendelse: SedHendelse): AvsenderMottaker? =
        avsenderMottaker(hendelseType, sedHendelse)

    private fun hentArkivsaksnummer(
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        saksInfoSamlet: SaksInfoSamlet?,
        identifisertPerson: IdentifisertPerson?
    ): Sak? {
        return if (sedHendelse.sedType == SedType.P2100 && hendelseType == MOTTATT) null else hentSakService.hentSak(
            sedHendelse.rinaSakId,
            saksInfoSamlet?.saksIdFraSed,
            saksInfoSamlet?.sakInformasjonFraPesys,
            identifisertPerson?.personRelasjon?.fnr
        ).also { logger.info("""SakId for rinaSak: ${sedHendelse.rinaSakId} pesysSakId: $it""".trimMargin()) }
    }

    private fun sendJournalpostRequest(
        journalpostRequest: OpprettJournalpostRequest,
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        navAnsattInfo: Pair<String, Enhet?>?,
        identifisertPerson: IdentifisertPerson?,
        tildeltJoarkEnhet: Enhet,
        aktoerId: String?,
        tema: Tema,
        currentSed: SED?,
        saksInfoSamlet: SaksInfoSamlet?
    ) {
        val journalPostResponse = journalpostService.sendJournalPost(
            journalpostRequest,
            sedHendelse,
            hendelseType,
            navAnsattInfo?.first
        )

        vurderSettAvbruttOgLagOppgave(
            identifisertPerson?.personRelasjon?.fnr,
            hendelseType,
            sedHendelse,
            journalPostResponse,
            tildeltJoarkEnhet,
            aktoerId,
            tema,
            saksInfoSamlet
        )

        if (hendelseType == MOTTATT && journalPostResponse?.journalpostferdigstilt == true) {
            logger.info("Oppretter BehandleOppgave til bucType: ${sedHendelse.bucType}")
            oppgaveService.opprettBehandleSedOppgave(
                journalPostResponse.journalpostId,
                tildeltJoarkEnhet,
                aktoerId,
                sedHendelse,
                tema = tema
            )
            kravInitialiseringsService.initKrav(
                sedHendelse,
                saksInfoSamlet?.sakInformasjonFraPesys,
                currentSed
            )
        } else {
            loggDersomIkkeBehSedOppgaveOpprettes(
                sedHendelse.bucType,
                sedHendelse,
                journalPostResponse?.journalpostferdigstilt,
                hendelseType
            )
        }

        produserStatistikkmelding(
            sedHendelse,
            tildeltJoarkEnhet.enhetsNr,
            saksInfoSamlet?.saktypeFraSed,
            hendelseType
        )
    }
    fun vurderSettAvbruttOgLagOppgave(
        fnr: Fodselsnummer?,
        hendelseType: HendelseType,
        sedHendelse: SedHendelse,
        journalPostResponse: OpprettJournalPostResponse?,
        tildeltJoarkEnhet: Enhet,
        aktoerId: String?,
        tema: Tema,
        saksInfoSamlet: SaksInfoSamlet? = null
    ) {
        // journalposten skal settes til avbrutt KUN VED UTGÅENDE SEDer ved manglende bruker/identifisertperson
        val journalpostErAvbrutt = journalpostService.journalpostSattTilAvbrutt(
            fnr,
            hendelseType,
            sedHendelse,
            journalPostResponse?.journalpostId,
        )

        // Oppdaterer distribusjonsinfo for utgående og maskinell journalføring (Ferdigstiller journalposten)
        if (journalPostResponse?.journalpostferdigstilt == true && hendelseType == SENDT) {
            journalpostService.oppdaterDistribusjonsinfo(journalPostResponse.journalpostId)
        }

        journalPostResponse?.journalpostferdigstilt.let { journalPostFerdig ->
            logger.info("Maskinelt journalført: $journalPostFerdig, sed: ${sedHendelse.sedType}, enhet: $tildeltJoarkEnhet, sattavbrutt: $journalpostErAvbrutt **********")
        }

        if (journalPostResponse?.journalpostferdigstilt == false && !journalpostErAvbrutt) {
            //Eddy vil ikke at vi skal opprette journalforingsoppgave for sendt P_BUC_02
            if (sedHendelse.bucType == P_BUC_02 && hendelseType == SENDT) {
                fnr?.getAge()?.let {
                    val erGjennySak = gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId)
                    if(it > 67 && erGjennySak){
                        logger.error("Utgående P_BUC_02, oppretter IKKE journalføringsoppgave")
                        throw Exception("Utgående P_BUC_02, oppretter IKKE journalføringsoppgave, slett ${sedHendelse.rinaSakId}")
                    }
                }
                logger.info("Utgående P_BUC_02 og SENDT, er gjennysak, oppretter oppgave")
            }
            val melding = OppgaveMelding(
                sedHendelse.sedType,
                journalPostResponse.journalpostId,
                tildeltJoarkEnhet,
                aktoerId,
                sedHendelse.rinaSakId,
                hendelseType,
                null,
                if (hendelseType == MOTTATT) OppgaveType.JOURNALFORING else OppgaveType.JOURNALFORING_UT,
                tema = tema,
                saksInfoSamlet?.advarsel
            ).also { logger.info("Tema for oppgaven er: ${it.tema}") }
            oppgaveService.opprettOppgaveMeldingPaaKafkaTopic(melding)
        }
    }

    private fun infologging(
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        saksInfoSamlet: SaksInfoSamlet?,
        identifisertPerson: IdentifisertPerson?
    ) {
        logger.info("""
           **********
                rinadokumentID: ${sedHendelse.rinaDokumentId},  rinasakID: ${sedHendelse.rinaSakId} 
                sedType: ${sedHendelse.sedType?.name}, bucType: ${sedHendelse.bucType}, hendelseType: $hendelseType, 
                sakId: ${saksInfoSamlet?.sakInformasjonFraPesys?.sakId}, sakType: ${saksInfoSamlet?.sakInformasjonFraPesys?.sakType?.name}  
                sakType: ${saksInfoSamlet?.saktypeFraSed?.name}, 
                identifisertPerson aktoerId: ${identifisertPerson?.aktoerId} 
           **********""".trimIndent()
        )
    }

    fun loggDersomIkkeBehSedOppgaveOpprettes(
        bucType: BucType?,
        sedHendelse: SedHendelse,
        journalpostferdigstilt: Boolean?,
        hendelseType: HendelseType
    ) = logger.info("""
            Oppretter ikke behandleSedOppgave for $hendelseType hendelse og ferdigstilt er: $journalpostferdigstilt
            bucType: $bucType 
            sedType: ${sedHendelse.sedType}
            rinanr: ${sedHendelse.rinaSakId}
        """.trimIndent()
    )

    /**
     * Avsender for utgående SED og mottaker for innkommende SED
     * Denne skal være satt og komme fra SED-hendelsen
     */
    private fun avsenderMottaker(
        hendelseType: HendelseType,
        sedHendelse: SedHendelse
    ): AvsenderMottaker? {
        val avsenderMottaker = when (hendelseType) {
            SENDT -> AvsenderMottaker(sedHendelse.mottakerId, UTL_ORG, sedHendelse.mottakerNavn, konverterGBUKLand(sedHendelse.mottakerLand))
            else -> AvsenderMottaker(sedHendelse.avsenderId, UTL_ORG, sedHendelse.avsenderNavn, konverterGBUKLand(sedHendelse.avsenderLand))
        }

        if(avsenderMottaker.id == null || avsenderMottaker.land == null ) {
            logger.error("Avsender/Mottaker for $hendelseType SED, mangler info: $avsenderMottaker")
            return null
        }
        return avsenderMottaker
    }

    private fun journalforingsEnhet(
        fdato: LocalDate?,
        identifisertPerson: IdentifisertPerson?,
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        sakInfo: SaksInfoSamlet?,
        harAdressebeskyttelse: Boolean,
        antallIdentifisertePersoner: Int,
        currentSed: SED?,
        tema: Tema
    ): Enhet {
        val bucType = sedHendelse.bucType
        val personRelasjon = identifisertPerson?.personRelasjon
        return if (fdato == null || personRelasjon?.fnr?.erNpid == true || fdato != personRelasjon?.fnr?.getBirthDate()) {
            logger.info("Fdato er forskjellig fra SED fnr, sender til $ID_OG_FORDELING fdato: $fdato identifisertperson sin fdato: ${personRelasjon?.fnr?.getBirthDate()}")
            ID_OG_FORDELING
        } else {
            val enhetFraRouting = oppgaveRoutingService.hentEnhet(
                OppgaveRoutingRequest.fra(
                    identifisertPerson,
                    fdato,
                    sakInfo?.saktypeFraSed,
                    sedHendelse,
                    hendelseType,
                    sakInfo?.sakInformasjonFraPesys,
                    harAdressebeskyttelse
                )
            )

            if (enhetFraRouting != ID_OG_FORDELING) return enhetFraRouting.also { logEnhet(enhetFraRouting, it) }

            else if(bucType in listOf(P_BUC_05, P_BUC_06)) return enhetDersomIdOgFordeling(identifisertPerson, fdato, antallIdentifisertePersoner).also { logEnhet(enhetFraRouting, it) }

            else return hentTemaService.enhetBasertPaaBehandlingstema(
                sedHendelse,
                sakInfo,
                identifisertPerson,
                antallIdentifisertePersoner,
                currentSed,
                tema
            )
                .also {
                    logEnhet(enhetFraRouting, it)
                    metricsCounterForEnhet(it)
                }
        }
    }

    fun metricsCounterForEnhet(enhet: Enhet) {
        try {
            Metrics.counter("journalforingsEnhet_fra_tema", "type", enhet.name).increment()
        } catch (_: Exception) {
            logger.warn("Metrics feilet på enhet: $enhet")
        }
    }

    private fun enhetDersomIdOgFordeling(
        identifisertPerson: IdentifisertPerson,
        fdato: LocalDate?,
        antallIdentifisertePersoner: Int
    ) : Enhet {
        val bosattNorge = identifisertPerson.landkode == "NOR"
        val pensjonist = Period.between(fdato, LocalDate.now()).years > 61
        val barn = Period.between(fdato, LocalDate.now()).years < 18
        val ufoereAlder = Period.between(fdato, LocalDate.now()).years in 19..61

        if (pensjonist || barn) return if (bosattNorge) NFP_UTLAND_AALESUND else PENSJON_UTLAND
        if (ufoereAlder) {
            if (bosattNorge) return if (antallIdentifisertePersoner <= 1) UFORE_UTLANDSTILSNITT else ID_OG_FORDELING
            if (antallIdentifisertePersoner <= 1) return UFORE_UTLAND
        }

        return ID_OG_FORDELING
    }

    private fun logEnhet(enhetFraRouting: Enhet, it: Enhet) =
        logger.info("Journalpost enhet: $enhetFraRouting rutes til -> Saksbehandlende enhet: $it")

    /**
     * PESYS støtter kun GB
     */
    private fun konverterGBUKLand(mottakerAvsenderLand: String?): String? =
        when (mottakerAvsenderLand) {
            "UK" -> "GB"
            else -> mottakerAvsenderLand
        }

    private fun produserStatistikkmelding(
        sedHendelse: SedHendelse,
        oppgaveEierEnhet: String?,
        sakType: SakType?,
        hendelsesType: HendelseType
    ) {
        statistikkPublisher.publiserStatistikkMelding(
            StatistikkMelding(
                sedHendelse.rinaSakId,
                sedHendelse.rinaDokumentId,
                sedHendelse.rinaDokumentVersjon,
                LocalDateTime.now(),
                oppgaveEierEnhet,
                sedHendelse.bucType!!,
                sedHendelse.sedType!!,
                sakType,
                hendelsesType
            )
        )
    }
}
