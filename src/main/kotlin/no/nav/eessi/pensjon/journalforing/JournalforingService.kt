package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
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
    private val secureLog = LoggerFactory.getLogger("secureLog")

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
        saktype: SakType? = null,
        sakInformasjon: SakInformasjon? = null,
        sed: SED? = null,
        harAdressebeskyttelse: Boolean = false,
        identifisertePersoner: Int,
        navAnsattInfo: Pair<String, Enhet?>? = null,
        gjennySakId: String? = null,
        kravTypeFraSed: String?
    ) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info(
                    """**********
                    rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId} sedType: ${sedHendelse.sedType?.name} bucType: ${sedHendelse.bucType}
                    hendelseType: $hendelseType, hentSak sakId: ${sakInformasjon?.sakId} sakType: ${sakInformasjon?.sakType?.name} på aktoerId: ${identifisertPerson?.aktoerId} sakType: ${saktype?.name}
                **********""".trimIndent()
                )

                val aktoerId = identifisertPerson?.aktoerId

                val tildeltJoarkEnhet = journalforingsEnhet(
                    fdato,
                    identifisertPerson,
                    saktype,
                    sedHendelse,
                    hendelseType,
                    sakInformasjon,
                    harAdressebeskyttelse,
                    identifisertePersoner,
                    kravTypeFraSed
                )

                // Henter dokumenter
                val (documents, _) = sedHendelse.run {
                    sedType?.let {
                        pdfService.hentDokumenterOgVedlegg(rinaSakId, rinaDokumentId, it)
                            .also { documentsAndAttachments ->
                                if (documentsAndAttachments.second.isNotEmpty()) {
                                    opprettBehandleSedOppgave(
                                        null,
                                        tildeltJoarkEnhet,
                                        aktoerId,
                                        sedHendelse,
                                        usupporterteFilnavn(documentsAndAttachments.second)
                                    )
                                }
                            }
                    } ?: throw IllegalStateException("sedType is null")
                }

                val institusjon = avsenderMottaker(hendelseType, sedHendelse)
                val tema = hentTema(sedHendelse, saktype, identifisertPerson?.personRelasjon?.fnr, identifisertePersoner).also {
                    logger.info("Hent tema gir: $it for ${sedHendelse.rinaSakId}, sedtype: ${sedHendelse.sedType}, buc: ${sedHendelse.bucType}")
                }

                // TODO: sende inn saksbehandlerInfo kun dersom det trengs til metoden under.
                // Oppretter journalpost

                val kravtype = sed?.nav?.krav?.type
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
                    tema = tema,
                    kravType = kravtype.toString()
                )

                val journalPostResponse = journalPostResponseOgRequest.first

                // ved utgående kan det være mulig å benytte info fra en tidligere journalpost
                skalJournalpostGjenbrukes(sedHendelse, journalPostResponseOgRequest)

                // journalposten skal settes til avbrutt ved manglende bruker/identifisertperson
                val sattStatusAvbrutt = journalpostService.settStatusAvbrutt(
                    identifisertPerson?.personRelasjon?.fnr,
                    hendelseType,
                    sedHendelse,
                    journalPostResponse,
                )

                // Oppdaterer distribusjonsinfo for utgående og maskinell journalføring (Ferdigstiller journalposten)
                if (journalPostResponse != null && journalPostResponse.journalpostferdigstilt && hendelseType == SENDT) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse.journalpostId)
                }

                journalPostResponse?.journalpostferdigstilt?.let { journalPostFerdig ->
                    logger.info("Maskinelt journalført: $journalPostFerdig, sed: ${sedHendelse.sedType}, enhet: $tildeltJoarkEnhet, sattavbrutt: $sattStatusAvbrutt **********")
                }

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

    private fun skalJournalpostGjenbrukes(
        sedHendelse: SedHendelse,
        journalPostResponseOgRequest: Pair<OpprettJournalPostResponse?, OpprettJournalpostRequest>
    ) {
        // henter lagret journalpost fra samme eux-rina-id hvis den eksisterer
        val lagretJournalPost = hentJournalPostFraS3ogSaf(sedHendelse.rinaSakId)

        val journalpostResponse = journalPostResponseOgRequest.first
        val journalpostRequest = journalPostResponseOgRequest.second

        // buc har en tidligere lagret journalpost som er ferdigstilt og skal benyttes som mal for neste
        if (lagretJournalPost != null) {
            val journalPostFraSaf = lagretJournalPost.first
            val journalPostFraS3 = lagretJournalPost.second

            // todo: ersattes med ny journalpost som bruker tidligere lagret data
            if(journalPostFraSaf.journalforendeEnhet != journalpostRequest.journalfoerendeEnhet?.enhetsNr ||
                journalPostFraSaf.tema != journalpostRequest.tema ||
                journalPostFraSaf.behandlingstema != journalpostRequest.behandlingstema)  {

                secureLog.info(
                    """Hentet journalpost for ${sedHendelse.rinaSakId}
                    lagret bruker saf:      ${journalPostFraSaf.bruker} : 
                                  sed:      ${journalpostRequest.bruker}
                    lagret JournalPostID:   ${journalPostFraSaf.journalpostId} : ${journalpostResponse?.journalpostId}
                    lagret SED:             ${journalPostFraS3.sedType} : ${sedHendelse.sedType}
                    lagret enhet:           ${journalPostFraSaf.journalforendeEnhet} : ${journalpostRequest.journalfoerendeEnhet?.enhetsNr} 
                    lagret tema:            ${journalPostFraSaf.tema} : ${journalpostRequest.tema}
                    lagret behandlingstema: ${journalPostFraSaf.behandlingstema} : ${journalpostRequest.behandlingstema}
                    lagret opprettetDato:   ${journalPostFraS3.opprettet}
                    retning:                ${journalpostRequest.journalpostType}""".trimIndent()
                )
            }
        }
        // buc har ingen lagret JP, men kan lagres da ferdigstilt er satt
        else if (journalpostResponse?.journalpostferdigstilt == true  && journalpostRequest.journalpostType == JournalpostType.UTGAAENDE){
            gcpStorageService.lagreJournalpostDetaljer(
                journalpostResponse.journalpostId,
                sedHendelse.rinaSakId,
                sedHendelse.rinaDokumentId,
                sedHendelse.sedType,
                journalpostRequest.eksternReferanseId
            )
        }
        // buc har ingen lagret JP, og heller ingen ferdgstilt
        else {
            logger.info("Journalpost ${journalpostResponse?.journalpostId} er ikke lagret, " +
                    "men mangler info da ferdigstilt: ${journalpostResponse?.journalpostferdigstilt}")
        }
    }

    fun hentJournalPostFraS3ogSaf(rinaSakId: String): Pair<JournalpostResponse, JournalpostDetaljer>? {
        return try {
            logger.info("Henter tilgjengelig informasjon fra GCP og SAF for buc: $rinaSakId")
            val lagretHendelse = gcpStorageService.hentFraJournal(rinaSakId) ?: return null
            val journalpostId = lagretHendelse.journalpostId ?: return null
            val journalpostDetaljer = safClient.hentJournalpost(journalpostId) ?: return null
            journalpostDetaljer to lagretHendelse
        } catch (e: Exception) {
            logger.error("Feiler under henting fra SAF: ${e.message}")
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

    private fun journalforingsEnhet(
        fdato: LocalDate?,
        identifisertPerson: IdentifisertPerson?,
        saktype: SakType?,
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        sakInformasjon: SakInformasjon?,
        harAdressebeskyttelse: Boolean,
        antallIdentifisertePersoner: Int,
        kravTypeFraSed: String?
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

            else return enhetBasertPaaBehandlingstema(
                sedHendelse,
                saktype,
                identifisertPerson,
                antallIdentifisertePersoner,
                kravTypeFraSed
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
        antallIdentifisertePersoner: Int,
        kravtypeFraSed: String?
    ): Enhet {
        val tema = hentTema(sedHendelse, saktype, identifisertPerson.fnr, antallIdentifisertePersoner)
        val behandlingstema = journalpostService.bestemBehandlingsTema(
            sedHendelse?.bucType!!,
            saktype,
            tema,
            antallIdentifisertePersoner,
            kravtypeFraSed

        )
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
    fun hentTema(
        sedhendelse: SedHendelse?,
        saktype: SakType?,
        fnr: Fodselsnummer?,
        identifisertePersoner: Int
    ) : Tema {
        return if (sedhendelse?.rinaSakId != null && gcpStorageService.gjennyFinnes(sedhendelse.rinaSakId)) {
            val blob = gcpStorageService.hentFraGjenny(sedhendelse.rinaSakId)
            if (blob?.contains("BARNEP") == true) EYBARNEP else OMSTILLING
        } else {
            when (sedhendelse?.sedType) {
                SedType.P2000, SedType.P2100 -> PENSJON
                SedType.P2200 -> UFORETRYGD
                else -> when {
                    sedhendelse?.bucType == P_BUC_03 || saktype == SakType.UFOREP -> UFORETRYGD
                    sedhendelse?.bucType in listOf(P_BUC_01, P_BUC_02) -> PENSJON
                    else -> {
                        if (muligUfore(sedhendelse, fnr, identifisertePersoner)) UFORETRYGD
                        else PENSJON
                    }
                }
            }
        }
    }

    private fun muligUfore(sedhendelse: SedHendelse?, fnr: Fodselsnummer?, identifisertePersoner: Int) : Boolean{
        val ufoereAlder = (fnr != null && !fnr.erNpid && Period.between(fnr.getBirthDate(), LocalDate.now()).years in 19..61)
        val muligUfoereBuc = sedhendelse?.bucType in listOf(P_BUC_05, P_BUC_06, P_BUC_10)
        return ufoereAlder && muligUfoereBuc && identifisertePersoner <= 1
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
