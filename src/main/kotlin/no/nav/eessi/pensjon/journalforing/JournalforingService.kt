package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.automatisering.AutomatiseringMelding
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.klienter.journalpost.AvsenderMottaker
import no.nav.eessi.pensjon.klienter.journalpost.IdType
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Behandlingstema.*
import no.nav.eessi.pensjon.oppgaverouting.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

@Service
class JournalforingService(
    private val journalpostService: JournalpostService,
    private val oppgaveRoutingService: OppgaveRoutingService,
    private val pdfService: PDFService,
    private val oppgaveHandler: OppgaveHandler,
    private val kravInitialiseringsService: KravInitialiseringsService,
    private val automatiseringStatistikkPublisher: AutomatiseringStatistikkPublisher,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private lateinit var journalforOgOpprettOppgaveForSed: MetricsHelper.Metric

    @Value("\${namespace}")
    lateinit var nameSpace: String

    @PostConstruct
    fun initMetrics() {
        journalforOgOpprettOppgaveForSed = metricsHelper.init("journalforOgOpprettOppgaveForSed")
    }

    /**
     * 1.) Henter dokumenter og vedlegg
     * 2.) Henter enhet
     * 3.) Oppretter journalpost
     * 4.) Ved utgpående automatisk journalføring -> oppdater distribusjonsinfo
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
        saktype: SakType?,
        offset: Long,
        sakInformasjon: SakInformasjon?,
        sed: SED?,
        harAdressebeskyttelse: Boolean = false,
        identifisertePersoner: Int
    ) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info(
                    """**********
                    rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId} sedType: ${sedHendelse.sedType?.name} bucType: ${sedHendelse.bucType}
                    hendelseType: $hendelseType, kafka offset: $offset, hentSak sakId: ${sakInformasjon?.sakId} sakType: ${sakInformasjon?.sakType?.name} på aktoerId: ${identifisertPerson?.aktoerId} sakType: ${saktype?.name}
                **********""".trimIndent()
                )

                // Henter dokumenter
                val (documents, uSupporterteVedlegg) = sedHendelse.run {
                    pdfService.hentDokumenterOgVedlegg(rinaSakId, rinaDokumentId, sedType!!)
                }

                val tildeltJoarkEnhet = journalforingsEnhet(
                    fdato,
                    identifisertPerson,
                    saktype,
                    sedHendelse,
                    hendelseType,
                    sakInformasjon,
                    harAdressebeskyttelse,
                    identifisertePersoner
                )
                val arkivsaksnummer = sakInformasjon?.sakId

                val institusjon = if(hendelseType == HendelseType.SENDT) {
                    AvsenderMottaker(sedHendelse.mottakerId, IdType.UTL_ORG, sedHendelse.mottakerNavn, konverterMottakerAvsenderLand(sedHendelse.mottakerLand))
                } else {
                    AvsenderMottaker(sedHendelse.avsenderId, IdType.UTL_ORG, sedHendelse.avsenderNavn, konverterMottakerAvsenderLand(sedHendelse.avsenderLand))
                }

                // Oppretter journalpost
                val journalPostResponse = journalpostService.opprettJournalpost(
                    rinaSakId = sedHendelse.rinaSakId,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    bucType = sedHendelse.bucType!!,
                    sedType = sedHendelse.sedType!!,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = tildeltJoarkEnhet,
                    arkivsaksnummer = arkivsaksnummer,
                    dokumenter = documents,
                    avsenderLand = sedHendelse.avsenderLand,
                    avsenderNavn = sedHendelse.avsenderNavn,
                    saktype = saktype,
                    institusjon = institusjon,
                    identifisertePersoner = identifisertePersoner
                )

                //Oppdaterer journalposten med status Avbrutt
                val bucsIkkeTilAvbrutt = listOf(R_BUC_02, M_BUC_02, M_BUC_03a, M_BUC_03b)
                val sedsIkkeTilAvbrutt = listOf(X001, X002, X003, X004, X005, X006, X007, X008, X009, X010, X013, X050, H001, H002, H020, H021, H070, H120, H121)

                val sattStatusAvbrutt = if (identifisertPerson?.personRelasjon?.fnr == null && hendelseType == HendelseType.SENDT &&
                    (sedHendelse.bucType !in bucsIkkeTilAvbrutt && sedHendelse.sedType !in sedsIkkeTilAvbrutt)) {
                    journalpostService.settStatusAvbrutt(journalPostResponse!!.journalpostId)
                    true
                } else false


                // Oppdaterer distribusjonsinfo for utgående og automatisk journalføring (Ferdigstiller journalposten)
                if (tildeltJoarkEnhet == AUTOMATISK_JOURNALFORING && hendelseType == HendelseType.SENDT) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse!!.journalpostId)
                }
                val aktoerId = identifisertPerson?.aktoerId

                if (!journalPostResponse!!.journalpostferdigstilt && !sattStatusAvbrutt) {
                    val melding = OppgaveMelding(
                        sedHendelse.sedType,
                        journalPostResponse.journalpostId,
                        tildeltJoarkEnhet,
                        aktoerId,
                        sedHendelse.rinaSakId,
                        hendelseType,
                        null,
                        OppgaveType.JOURNALFORING
                    )
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                }
                val oppgaveEnhet = hentOppgaveEnhet(
                        tildeltJoarkEnhet,
                        identifisertPerson,
                        fdato,
                        saktype,
                        sedHendelse,
                        harAdressebeskyttelse
                    ).takeIf { tildeltJoarkEnhet == AUTOMATISK_JOURNALFORING }

                if (uSupporterteVedlegg.isNotEmpty()) {
                    if (oppgaveEnhet != null) {
                        opprettBehandleSedOppgave(
                            null,
                            oppgaveEnhet,
                            aktoerId,
                            sedHendelse,
                            usupporterteFilnavn(uSupporterteVedlegg)
                        )
                    }
                }

                val bucType = sedHendelse.bucType
                if ((bucType == P_BUC_01 || bucType == P_BUC_03) && (hendelseType == HendelseType.MOTTATT && tildeltJoarkEnhet == AUTOMATISK_JOURNALFORING && journalPostResponse.journalpostferdigstilt)) {
                    logger.info("Oppretter BehandleOppgave til bucType: $bucType")
                    if (oppgaveEnhet != null) {
                        opprettBehandleSedOppgave(
                            journalPostResponse.journalpostId,
                            oppgaveEnhet,
                            aktoerId,
                            sedHendelse
                        )
                    }

                    kravInitialiseringsService.initKrav(
                        sedHendelse,
                        sakInformasjon,
                        sed
                    )
                }

                produserAutomatiseringsmelding(sedHendelse.rinaSakId,
                    sedHendelse.rinaDokumentId,
                    sedHendelse.rinaDokumentVersjon,
                    java.time.LocalDateTime.now(),
                    tildeltJoarkEnhet == AUTOMATISK_JOURNALFORING,
                    tildeltJoarkEnhet.enhetsNr,
                    sedHendelse.bucType!!,
                    sedHendelse.sedType!!,
                    saktype,
                    hendelseType
                )
            } catch (ex: MismatchedInputException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Det oppstod en uventet feil ved journalforing av hendelse", ex)
                throw ex
            }
        }
    }

    private fun journalforingsEnhet(
        fdato: LocalDate?,
        identifisertPerson: IdentifisertPerson?,
        saktype: SakType?,
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        sakInformasjon: SakInformasjon?,
        harAdressebeskyttelse: Boolean,
        antallIdentifisertePersoner: Int
    ): Enhet {
        val bucType = sedHendelse.bucType
        return if (fdato == null || fdato != identifisertPerson?.personRelasjon?.fnr?.getBirthDate()) {
            logger.info("Fdato er forskjellig fra SED fnr, sender til $ID_OG_FORDELING fdato: $fdato identifisertpersonfnr: ${identifisertPerson?.personRelasjon?.fnr?.getBirthDate()}")
            ID_OG_FORDELING
        } else {
            val enhetFraRouting = oppgaveRoutingService.hentEnhet(
                OppgaveRoutingRequest.fra(
                    identifisertPerson,
                    fdato,
                    saktype,
                    sedHendelse,
                    hendelseType,
                    sakInformasjon,
                    harAdressebeskyttelse
                )
            )
            if(enhetFraRouting == AUTOMATISK_JOURNALFORING){
                return AUTOMATISK_JOURNALFORING
            }

            val over62Aar = Period.between(fdato, LocalDate.now()).years > 61
            val barn = Period.between(fdato, LocalDate.now()).years < 18
            val under62AarIkkeBarn = Period.between(fdato, LocalDate.now()).years in 19..61

            if (enhetFraRouting == ID_OG_FORDELING && bucType in listOf(P_BUC_05, P_BUC_06)) {
                if (over62Aar || barn) {
                    return if(identifisertPerson.landkode == "NOR"){
                        NFP_UTLAND_AALESUND.also { logEnhet(enhetFraRouting, it) }
                    } else PENSJON_UTLAND.also { logEnhet(enhetFraRouting, it) }
                }
                if (under62AarIkkeBarn) {
                    if (identifisertPerson.landkode == "NOR") {
                        return if (antallIdentifisertePersoner <= 1) {
                            UFORE_UTLANDSTILSNITT.also { logEnhet(enhetFraRouting, it) }
                        } else ID_OG_FORDELING
                    }
                    if (antallIdentifisertePersoner <= 1) {
                        return UFORE_UTLAND.also { logEnhet(enhetFraRouting, it) }
                    }
                }
                ID_OG_FORDELING
            }

            if (enhetFraRouting == ID_OG_FORDELING ) {
                val behandlingstema = journalpostService.bestemBehandlingsTema(
                    bucType!!, saktype,
                    journalpostService.hentTema(
                        bucType,
                        saktype,
                        identifisertPerson.fnr,
                        antallIdentifisertePersoner
                    ),
                    antallIdentifisertePersoner,
                )
                logger.info("landkode: ${identifisertPerson.landkode} og behandlingstema: $behandlingstema med enhet:$enhetFraRouting")
                return if (identifisertPerson.landkode == "NOR" ) {
                    when (behandlingstema) {
                        GJENLEVENDEPENSJON, Behandlingstema.BARNEP, ALDERSPENSJON, TILBAKEBETALING -> NFP_UTLAND_AALESUND.also { logEnhet(enhetFraRouting, it) }
                        UFOREPENSJON -> UFORE_UTLANDSTILSNITT.also { logEnhet(enhetFraRouting, it) }
                    }
                } else when (behandlingstema) {
                    UFOREPENSJON -> UFORE_UTLANDSTILSNITT.also { logEnhet(enhetFraRouting, it) }
                    GJENLEVENDEPENSJON, Behandlingstema.BARNEP, ALDERSPENSJON, TILBAKEBETALING -> PENSJON_UTLAND.also { logEnhet(enhetFraRouting, it) }
                }
            }
            enhetFraRouting.also { logEnhet(enhetFraRouting, it) }
        }
    }

    private fun logEnhet(enhetFraRouting: Enhet, it: Enhet) = logger.info("Journalpost enhet: $enhetFraRouting rutes til -> Saksbehandlende enhet: $it")

    /**
     * PESYS støtter kun GB
     */
    private fun konverterMottakerAvsenderLand(mottakerAvsenderLand: String?): String? =
        if (mottakerAvsenderLand == "UK") "GB"
        else mottakerAvsenderLand

    private fun produserAutomatiseringsmelding(
        bucId: String,
        sedId: String,
        sedVersjon: String,
        opprettetTidspunkt: java.time.LocalDateTime,
        bleAutomatisert: Boolean,
        oppgaveEierEnhet: String?,
        bucType: BucType,
        sedType: SedType,
        sakType: SakType?,
        hendelsesType: HendelseType
    ) {
        automatiseringStatistikkPublisher.publiserAutomatiseringStatistikk(AutomatiseringMelding(
            bucId,
            sedId,
            sedVersjon,
            opprettetTidspunkt,
            bleAutomatisert,
            oppgaveEierEnhet,
            bucType,
            sedType,
            sakType,
            hendelsesType))
    }

    private fun opprettBehandleSedOppgave(
        journalpostId: String? = null,
        oppgaveEnhet: Enhet,
        aktoerId: String? = null,
        sedHendelseModel: SedHendelse,
        uSupporterteVedlegg: String? = null
    ) {
        if(sedHendelseModel.avsenderLand != "NO") {
            val oppgave = OppgaveMelding(
                sedHendelseModel.sedType,
                journalpostId,
                oppgaveEnhet,
                aktoerId,
                sedHendelseModel.rinaSakId,
                HendelseType.MOTTATT,
                uSupporterteVedlegg,
                OppgaveType.BEHANDLE_SED
            )
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(oppgave)
        } else logger.warn("Nå har du forsøkt å opprette en BEHANDLE_SED oppgave, men avsenderland er Norge.")
    }

    private fun hentOppgaveEnhet(
        tildeltEnhet: Enhet,
        identifisertPerson: IdentifisertPerson?,
        fdato: LocalDate?,
        saktype: SakType?,
        sedHendelseModel: SedHendelse,
        harAdressebeskyttelse: Boolean,
    ): Enhet {
        return if (tildeltEnhet == AUTOMATISK_JOURNALFORING) {
            oppgaveRoutingService.hentEnhet(
                OppgaveRoutingRequest.fra(
                    identifisertPerson,
                    fdato!!,
                    saktype,
                    sedHendelseModel,
                    HendelseType.MOTTATT,
                    null,
                    harAdressebeskyttelse
                )
            )
        } else
            tildeltEnhet
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<SedVedlegg>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }
}
