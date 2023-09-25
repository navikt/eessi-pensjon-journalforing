package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.automatisering.AutomatiseringMelding
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.eux.model.BucType.M_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.M_BUC_03a
import no.nav.eessi.pensjon.eux.model.BucType.M_BUC_03b
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_05
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.H001
import no.nav.eessi.pensjon.eux.model.SedType.H002
import no.nav.eessi.pensjon.eux.model.SedType.H020
import no.nav.eessi.pensjon.eux.model.SedType.H021
import no.nav.eessi.pensjon.eux.model.SedType.H070
import no.nav.eessi.pensjon.eux.model.SedType.H120
import no.nav.eessi.pensjon.eux.model.SedType.H121
import no.nav.eessi.pensjon.eux.model.SedType.X001
import no.nav.eessi.pensjon.eux.model.SedType.X002
import no.nav.eessi.pensjon.eux.model.SedType.X003
import no.nav.eessi.pensjon.eux.model.SedType.X004
import no.nav.eessi.pensjon.eux.model.SedType.X005
import no.nav.eessi.pensjon.eux.model.SedType.X006
import no.nav.eessi.pensjon.eux.model.SedType.X007
import no.nav.eessi.pensjon.eux.model.SedType.X008
import no.nav.eessi.pensjon.eux.model.SedType.X009
import no.nav.eessi.pensjon.eux.model.SedType.X010
import no.nav.eessi.pensjon.eux.model.SedType.X013
import no.nav.eessi.pensjon.eux.model.SedType.X050
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.klienter.journalpost.AvsenderMottaker
import no.nav.eessi.pensjon.klienter.journalpost.IdType
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Behandlingstema.ALDERSPENSJON
import no.nav.eessi.pensjon.models.Behandlingstema.GJENLEVENDEPENSJON
import no.nav.eessi.pensjon.models.Behandlingstema.TILBAKEBETALING
import no.nav.eessi.pensjon.models.Behandlingstema.UFOREPENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.oppgaverouting.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.oppgaverouting.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
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
    private lateinit var journalforOgOpprettOppgaveForSedMedUkjentPerson: MetricsHelper.Metric

    @Value("\${namespace}")
    lateinit var nameSpace: String

    @PostConstruct
    fun initMetrics() {
        journalforOgOpprettOppgaveForSed = metricsHelper.init("journalforOgOpprettOppgaveForSed")
        journalforOgOpprettOppgaveForSedMedUkjentPerson = metricsHelper.init("journalforOgOpprettOppgaveForSed")
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
                    hendelseType: $hendelseType, hentSak sakId: ${sakInformasjon?.sakId} sakType: ${sakInformasjon?.sakType?.name} på aktoerId: ${identifisertPerson?.aktoerId} sakType: ${saktype?.name}
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

                val institusjon = avsenderMottaker(hendelseType, sedHendelse)

                // Oppretter journalpost
                val journalPostResponse = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = tildeltJoarkEnhet,
                    arkivsaksnummer = arkivsaksnummer,
                    dokumenter = documents,
                    saktype = saktype,
                    institusjon = institusjon,
                    identifisertePersoner = identifisertePersoner
                )

                val sattStatusAvbrutt = sattAvbrutt(
                    identifisertPerson,
                    hendelseType,
                    sedHendelse,
                    journalPostResponse,
                )

                // Oppdaterer distribusjonsinfo for utgående og automatisk journalføring (Ferdigstiller journalposten)
                if (journalPostResponse != null && journalPostResponse.journalpostferdigstilt && hendelseType == SENDT) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse.journalpostId)
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
                    )

                if (uSupporterteVedlegg.isNotEmpty()) {
                    opprettBehandleSedOppgave(
                        null,
                        oppgaveEnhet,
                        aktoerId,
                        sedHendelse,
                        usupporterteFilnavn(uSupporterteVedlegg)
                    )
                }

                val bucType = sedHendelse.bucType
                if ((bucType == P_BUC_01 || bucType == P_BUC_03) && (hendelseType == MOTTATT && journalPostResponse.journalpostferdigstilt)) {
                    logger.info("Oppretter BehandleOppgave til bucType: $bucType")
                    opprettBehandleSedOppgave(
                        journalPostResponse.journalpostId,
                        oppgaveEnhet,
                        aktoerId,
                        sedHendelse
                    )

                    kravInitialiseringsService.initKrav(
                        sedHendelse,
                        sakInformasjon,
                        sed
                    )
                }

                produserAutomatiseringsmelding(
                    sedHendelse,
                    tildeltJoarkEnhet == AUTOMATISK_JOURNALFORING,
                    tildeltJoarkEnhet.enhetsNr,
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

    /**
     *     Denne metoden blir kun brukt i behandlingen av utgående SEDer der person ikke er identifiserbar, men SEDen inneholder pesys sakId.
     * 1.) Henter dokumenter og vedlegg
     * 2.) Henter enhet
     * 3.) Oppretter journalpost
     * 4.) Lage journalførings-oppgave
     * 5.) Hent oppgave-enhet
     * 6.) Generer statisikk melding
     */
    fun journalforUkjentPersonKjentPersysSakId(
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        fdato: LocalDate?,
        saktype: SakType?,
        pesysSakId: String,
    ) {
        journalforOgOpprettOppgaveForSedMedUkjentPerson.measure {
            try {
                logger.info(
                    """********** JOURNALFØRING FOR UKJENT PERSON MED KJENT PESYS SAKSID
                    rinadokumentID: ${sedHendelse.rinaDokumentId} 
                    rinasakID: ${sedHendelse.rinaSakId} 
                    sedType: ${sedHendelse.sedType?.name} 
                    bucType: ${sedHendelse.bucType}
                    hendelseType: $hendelseType, 
                    pesysSakId: $pesysSakId, 
                    sakType: ${saktype?.name}
                **********""".trimIndent()
                )

                // Henter dokumenter
                val (documents, uSupporterteVedlegg) = sedHendelse.run {
                    pdfService.hentDokumenterOgVedlegg(rinaSakId, rinaDokumentId, sedType!!)
                }

                val institusjon = avsenderMottaker(hendelseType, sedHendelse)

                // Oppretter journalpost
                val journalPostResponse = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    fnr = null,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = ID_OG_FORDELING,
                    arkivsaksnummer = pesysSakId,
                    dokumenter = documents,
                    saktype = saktype,
                    institusjon = institusjon,
                    identifisertePersoner = 0
                )

                oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(
                    OppgaveMelding(
                        sedHendelse.sedType,
                        journalPostResponse?.journalpostId,
                        ID_OG_FORDELING,
                        null,
                        sedHendelse.rinaSakId,
                        hendelseType,
                        null,
                        OppgaveType.JOURNALFORING
                    )
                )

                if (uSupporterteVedlegg.isNotEmpty()) {
                    opprettBehandleSedOppgave(
                        null,
                        ID_OG_FORDELING,
                        null,
                        sedHendelse,
                        usupporterteFilnavn(uSupporterteVedlegg)
                    )
                }

                produserAutomatiseringsmelding(
                    sedHendelse,
                    false,
                    oppgaveEierEnhet = ID_OG_FORDELING.name,
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

    private fun avsenderMottaker(
        hendelseType: HendelseType,
        sedHendelse: SedHendelse
    ): AvsenderMottaker {
        val institusjon = if (hendelseType == SENDT) AvsenderMottaker(
            sedHendelse.mottakerId,
            IdType.UTL_ORG,
            sedHendelse.mottakerNavn,
            konverterMottakerAvsenderLand(sedHendelse.mottakerLand)
        ) else AvsenderMottaker(
            sedHendelse.avsenderId,
            IdType.UTL_ORG,
            sedHendelse.avsenderNavn,
            konverterMottakerAvsenderLand(sedHendelse.avsenderLand)
        )
        return institusjon
    }

    private fun sattAvbrutt(
        identifisertPerson: IdentifisertPerson?,
        hendelseType: HendelseType,
        sedHendelse: SedHendelse,
        journalPostResponse: OpprettJournalPostResponse?
    ): Boolean {
        //Oppdaterer journalposten med status Avbrutt
        val bucsIkkeTilAvbrutt = listOf(R_BUC_02, M_BUC_02, M_BUC_03a, M_BUC_03b)
        val sedsIkkeTilAvbrutt = listOf(X001, X002, X003, X004, X005, X006, X007, X008, X009, X010, X013, X050, H001, H002, H020, H021, H070, H120, H121)

        logger.info("Vurderer satt avbrutt, hendelseType: $hendelseType, sedhendelse: ${sedHendelse.bucType}, journalpostId: ${journalPostResponse?.journalpostId}")

        val sattStatusAvbrutt =
            if (identifisertPerson?.personRelasjon?.fnr == null && hendelseType == SENDT &&
                (sedHendelse.bucType !in bucsIkkeTilAvbrutt && sedHendelse.sedType !in sedsIkkeTilAvbrutt)
            ) {
                journalpostService.settStatusAvbrutt(journalPostResponse!!.journalpostId)
                true
            } else false
        return sattStatusAvbrutt .also { logger.info("Journalpost settes til avbrutt==$it") }
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
        val personRelasjon = identifisertPerson?.personRelasjon
        return if (fdato == null || personRelasjon?.fnr?.erNpid == true || fdato != personRelasjon?.fnr?.getBirthDate()) {
            logger.info("Fdato er forskjellig fra SED fnr, sender til $ID_OG_FORDELING fdato: $fdato identifisertpersonfnr: ${personRelasjon?.fdato}")
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
        sedHendelse: SedHendelse,
        bleAutomatisert: Boolean,
        oppgaveEierEnhet: String?,
        sakType: SakType?,
        hendelsesType: HendelseType
    ) {
        automatiseringStatistikkPublisher.publiserAutomatiseringStatistikk(AutomatiseringMelding(
            sedHendelse.rinaSakId,
            sedHendelse.rinaDokumentId,
            sedHendelse.rinaDokumentVersjon,
            LocalDateTime.now(),
            bleAutomatisert,
            oppgaveEierEnhet,
            sedHendelse.bucType!!,
            sedHendelse.sedType!!,
            sakType,
            hendelsesType))
    }

    fun opprettBehandleSedOppgave(
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
                MOTTATT,
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
                    MOTTATT,
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
