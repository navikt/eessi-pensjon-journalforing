package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.JournalpostDetaljer
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Behandlingstema.*
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkMelding
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
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
    private val gcpStorageService: GcpStorageService,
    private val statistikkPublisher: StatistikkPublisher,
    private val safClient: SafClient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private lateinit var journalforOgOpprettOppgaveForSed: MetricsHelper.Metric
    private lateinit var journalforOgOpprettOppgaveForSedMedUkjentPerson: MetricsHelper.Metric

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
        saktype: SakType?,
        sakInformasjon: SakInformasjon?,
        sed: SED?,
        harAdressebeskyttelse: Boolean = false,
        identifisertePersoner: Int,
        navAnsattInfo: Pair<String, Enhet?>? = null,
        gjennySakId: String? = null
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

                val institusjon = avsenderMottaker(hendelseType, sedHendelse)
                val tema = hentTema(sedHendelse.bucType!!, saktype, identifisertPerson?.personRelasjon?.
                fnr, identifisertePersoner, sedHendelse.rinaSakId)

                // TODO: sende inn saksbehandlerInfo kun dersom det trengs til metoden under.
                // Oppretter journalpost
                val journalPostResponseOgRequest = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = tildeltJoarkEnhet,
                    arkivsaksnummer = hentSak(sedHendelse.rinaSakId, gjennySakId, sakInformasjon),
                    dokumenter = documents,
                    saktype = saktype,
                    institusjon = institusjon,
                    identifisertePersoner = identifisertePersoner,
                    saksbehandlerInfo = navAnsattInfo,
                    tema = tema
                )

                val journalPostResponse = journalPostResponseOgRequest.first
                val tidligereJournalPost = hentJournalPostFraS3ogSaf(sedHendelse.rinaSakId)?.first

                if (tidligereJournalPost != null) {
                    //henter lagret journalpost for å hente sed informasjon
                    val lagretHJournalPost = hentJournalPostFraS3ogSaf(sedHendelse.rinaSakId)?.second

                    logger.info("Hentet journalpost fra SAF: ${tidligereJournalPost.journalpostId} " +
                            "lagret sed: ${lagretHJournalPost?.sedType} : ${sedHendelse.sedType}" +
                            "lagret enhet ${tidligereJournalPost.journalforendeEnhet} : ${journalPostResponseOgRequest.second.journalfoerendeEnhet} " +
                            "lagret tema: ${tidligereJournalPost.tema} : ${journalPostResponseOgRequest.second.tema}" +
                            "lagret behandlingstema: ${tidligereJournalPost.behandlingstema} : ${journalPostResponseOgRequest.second.behandlingstema}")
                }
                else {
                    gcpStorageService.lagreJournalpostDetaljer(
                        journalPostResponse?.journalpostId,
                        sedHendelse.rinaSakId,
                        sedHendelse.rinaDokumentId,
                        sedHendelse.sedType,
                        journalPostResponseOgRequest.second.eksternReferanseId
                    )
                }

                val sattStatusAvbrutt = sattAvbrutt(
                    identifisertPerson,
                    hendelseType,
                    sedHendelse,
                    journalPostResponse,
                )

                // Oppdaterer distribusjonsinfo for utgående og maskinell journalføring (Ferdigstiller journalposten)
                if (journalPostResponse != null && journalPostResponse.journalpostferdigstilt && hendelseType == SENDT) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse.journalpostId)
                }

                val aktoerId = identifisertPerson?.aktoerId
                logger.info(
                    "********** Maskinelt journalført:${journalPostResponse?.journalpostferdigstilt}" +
                            ", sed: ${sedHendelse.sedType}" +
                            ", enhet: $tildeltJoarkEnhet, sattavbrutt: $sattStatusAvbrutt **********"
                )

                if (!journalPostResponse!!.journalpostferdigstilt && !sattStatusAvbrutt) {
                    val melding = OppgaveMelding(
                        sedHendelse.sedType,
                        journalPostResponse.journalpostId,
                        tildeltJoarkEnhet,
                        aktoerId,
                        sedHendelse.rinaSakId,
                        hendelseType,
                        null,
                        OppgaveType.JOURNALFORING,
                        tema = tema
                    )
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                }

                if (uSupporterteVedlegg.isNotEmpty()) {
                    opprettBehandleSedOppgave(
                        null,
                        tildeltJoarkEnhet,
                        aktoerId,
                        sedHendelse,
                        usupporterteFilnavn(uSupporterteVedlegg)
                    )
                }

                //Fag har bestemt at alle mottatte seder som ferdigstilles maskinelt skal det opprettes BEHANDLE_SED oppgave for
                if ((hendelseType == MOTTATT && journalPostResponse.journalpostferdigstilt)) {
                    logger.info("Oppretter BehandleOppgave til bucType: ${sedHendelse.bucType}")
                    opprettBehandleSedOppgave(
                        journalPostResponse.journalpostId,
                        tildeltJoarkEnhet,
                        aktoerId,
                        sedHendelse
                    )
                    kravInitialiseringsService.initKrav(
                        sedHendelse,
                        sakInformasjon,
                        sed
                    )
                } else loggDersomIkkeBehSedOppgaveOpprettes(sedHendelse.bucType, sedHendelse, journalPostResponse.journalpostferdigstilt, hendelseType)

                produserStatistikkmelding(
                    sedHendelse,
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

    fun hentJournalPostFraS3ogSaf(rinaSakId: String) : Pair<JournalpostResponse?, JournalpostDetaljer>? {
        return try {
            logger.info("Henter tilgjengelig informasjon fra GCP og SAF for buc: $rinaSakId")
            val lagretHendelse = gcpStorageService.hentFraJournal(rinaSakId)
            lagretHendelse?.journalpostId?.let { Pair(safClient.hentJournalpost(it), lagretHendelse) }
        } catch (e: Exception) {
            logger.error("Feiler under henting fra SAF" + e.message)
            null
        }
    }

    fun loggDersomIkkeBehSedOppgaveOpprettes(
        bucType: BucType?,
        sedHendelse: SedHendelse,
        journalpostferdigstilt: Boolean,
        hendelseType: HendelseType
    ) = logger.info("""
            Oppretter ikke behandleSedOppgave for $hendelseType hendelse og ferdigstilt er: $journalpostferdigstilt
            bucType: $bucType 
            sedType: ${sedHendelse.sedType}
            rinanr: ${sedHendelse.rinaSakId}
        """.trimIndent()
    )

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
                val tema = hentTema(sedHendelse.bucType!!, saktype, sedHendelse.navBruker, 0, sedHendelse.rinaSakId)

                // Oppretter journalpost
                val journalPostResponse = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    fnr = null,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = ID_OG_FORDELING,
                    arkivsaksnummer = hentSak(sedHendelse.rinaSakId, pesysSakId, null),
                    dokumenter = documents,
                    saktype = saktype,
                    institusjon = institusjon,
                    identifisertePersoner = 0,
                    saksbehandlerInfo = null,
                    tema = tema
                )

                oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(
                    OppgaveMelding(
                        sedHendelse.sedType,
                        journalPostResponse.first?.journalpostId,
                        ID_OG_FORDELING,
                        null,
                        sedHendelse.rinaSakId,
                        hendelseType,
                        null,
                        OppgaveType.JOURNALFORING,
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

                produserStatistikkmelding(
                    sedHendelse,
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
        return when (hendelseType) {
            SENDT -> AvsenderMottaker(
                sedHendelse.mottakerId, IdType.UTL_ORG, sedHendelse.mottakerNavn, konverterGBUKLand(sedHendelse.mottakerLand)
            )
            else -> AvsenderMottaker(
                sedHendelse.avsenderId, IdType.UTL_ORG, sedHendelse.avsenderNavn, konverterGBUKLand(sedHendelse.avsenderLand)
            )
        }
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

        val sattStatusAvbrutt =
            if (identifisertPerson?.personRelasjon?.fnr == null && hendelseType == SENDT &&
                sedHendelse.bucType !in bucsIkkeTilAvbrutt && sedHendelse.sedType !in sedsIkkeTilAvbrutt
            ) {
                journalpostService.settStatusAvbrutt(journalPostResponse!!.journalpostId)
                true
            } else false
        return sattStatusAvbrutt.also {
            logger.info("Journalpost settes til avbrutt == $it, $hendelseType, sedhendelse: ${sedHendelse.bucType}, journalpostId: ${journalPostResponse?.journalpostId}")
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
        val personRelasjon = identifisertPerson?.personRelasjon
        return if (fdato == null || personRelasjon?.fnr?.erNpid == true || fdato != personRelasjon?.fnr?.getBirthDate()) {
            logger.info("Fdato er forskjellig fra SED fnr, sender til $ID_OG_FORDELING fdato: $fdato identifisertperson sin fdato: ${personRelasjon?.fnr?.getBirthDate()}")
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

            if (enhetFraRouting != ID_OG_FORDELING) return enhetFraRouting.also { logEnhet(enhetFraRouting, it) }

            else if(bucType in listOf(P_BUC_05, P_BUC_06)) return enhetDersomIdOgFordeling(identifisertPerson, fdato, antallIdentifisertePersoner).also { logEnhet(enhetFraRouting, it) }

            else return enhetBasertPaaBehandlingstema(sedHendelse, saktype, identifisertPerson, antallIdentifisertePersoner)
                .also {
                    logEnhet(enhetFraRouting, it)
                    metricsCounterForEnhet(it)
                }
        }
    }

    fun metricsCounterForEnhet(enhet: Enhet) {
        try {
            Metrics.counter("journalforingsEnhet_fra_tema", "type", enhet.name).increment()
        } catch (e: Exception) {
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

        if (pensjonist || barn) {
            return if (bosattNorge) {
                NFP_UTLAND_AALESUND
            } else PENSJON_UTLAND
        }
        if (ufoereAlder) {
            if (bosattNorge) {
                return if (antallIdentifisertePersoner <= 1) {
                    UFORE_UTLANDSTILSNITT
                } else ID_OG_FORDELING
            }
            if (antallIdentifisertePersoner <= 1) {
                return UFORE_UTLAND
            }
        }
        return ID_OG_FORDELING
    }

    private fun enhetBasertPaaBehandlingstema(
        sedHendelse: SedHendelse?,
        saktype: SakType?,
        identifisertPerson: IdentifisertPerson,
        antallIdentifisertePersoner: Int
    ): Enhet {
        val tema = hentTema(sedHendelse?.bucType!!, saktype, identifisertPerson.fnr, antallIdentifisertePersoner, sedHendelse.rinaSakId)
        val behandlingstema = journalpostService.bestemBehandlingsTema(sedHendelse.bucType!!, saktype, tema, antallIdentifisertePersoner)
        logger.info("${sedHendelse.sedType} gir landkode: ${identifisertPerson.landkode}, behandlingstema: $behandlingstema, tema: $tema")

        return if (identifisertPerson.landkode == "NOR") {
            when (behandlingstema) {
                GJENLEVENDEPENSJON, BARNEP, ALDERSPENSJON, TILBAKEBETALING -> NFP_UTLAND_AALESUND
                UFOREPENSJON -> UFORE_UTLANDSTILSNITT
            }
        } else when (behandlingstema) {
            GJENLEVENDEPENSJON, BARNEP, ALDERSPENSJON, TILBAKEBETALING -> PENSJON_UTLAND
            UFOREPENSJON -> UFORE_UTLANDSTILSNITT
        }
    }


    /**
     * Tema er PENSJON såfremt det ikke er en
     * - uføre buc (P_BUC_03)
     * - saktype er UFØRETRYGD
     */
    //TODO: Fikse sånn at denne håndterer både npid og fnr
    fun hentTema(
        bucType: BucType,
        saktype: SakType?,
        fnr: Fodselsnummer?,
        identifisertePersoner: Int,
        euxCaseId: String
    ) : Tema {
        return if (gcpStorageService.gjennyFinnes(euxCaseId)) {
            val blob = gcpStorageService.hentFraGjenny(euxCaseId,)
            if (blob?.contains("BARNEP") == true) EYBARNEP else OMSTILLING
        } else {
            val ufoereAlder = if (fnr != null && !fnr.erNpid) Period.between(fnr.getBirthDate(), LocalDate.now()).years in 19..61 else false
            if (saktype == SakType.UFOREP || bucType == P_BUC_03 && saktype == null) UFORETRYGD
            else {
                val muligUfoereBuc = bucType in listOf(P_BUC_05, P_BUC_06)
                if (muligUfoereBuc && ufoereAlder && identifisertePersoner <= 1) UFORETRYGD else PENSJON
            }
        }
    }

    fun hentSak(
        euxCaseId: String,
        sakIdFraSed: String? = null,
        sakInformasjon: SakInformasjon? = null
    ): Sak? {
        return if (gcpStorageService.gjennyFinnes(euxCaseId,) && sakIdFraSed != null)
            Sak("FAGSAK", sakIdFraSed, "EY")
        else if (sakInformasjon?.sakId != null)
            Sak("FAGSAK", sakInformasjon.sakId!!, "PP01")
        else null
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

    fun opprettBehandleSedOppgave(
        journalpostId: String? = null,
        oppgaveEnhet: Enhet,
        aktoerId: String? = null,
        sedHendelseModel: SedHendelse,
        uSupporterteVedlegg: String? = null
    ) {
        if (sedHendelseModel.avsenderLand != "NO") {
            val oppgave = OppgaveMelding(
                sedHendelseModel.sedType,
                journalpostId,
                oppgaveEnhet,
                aktoerId,
                sedHendelseModel.rinaSakId,
                MOTTATT,
                uSupporterteVedlegg,
                OppgaveType.BEHANDLE_SED,
            )
            oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(oppgave)
        } else logger.warn("Nå har du forsøkt å opprette en BEHANDLE_SED oppgave, men avsenderland er Norge.")
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<SedVedlegg>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }
}
