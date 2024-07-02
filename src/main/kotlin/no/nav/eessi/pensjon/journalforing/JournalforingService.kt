package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.JournalpostDetaljer
import no.nav.eessi.pensjon.journalforing.Journalstatus.UNDER_ARBEID
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
import no.nav.eessi.pensjon.models.SaksInfoSamlet
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
import no.nav.eessi.pensjon.utils.toJson
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
        saksInfoSamlet: SaksInfoSamlet? = null,
        sed: SED? = null,
        harAdressebeskyttelse: Boolean = false,
        identifisertePersoner: Int,
        navAnsattInfo: Pair<String, Enhet?>? = null,
        kravTypeFraSed: KravType?
    ) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info(
                    """**********
                    rinadokumentID: ${sedHendelse.rinaDokumentId},  rinasakID: ${sedHendelse.rinaSakId} 
                    sedType: ${sedHendelse.sedType?.name}, bucType: ${sedHendelse.bucType}, hendelseType: $hendelseType, 
                    sakId: ${saksInfoSamlet?.sakInformasjon?.sakId}, sakType: ${saksInfoSamlet?.sakInformasjon?.sakType?.name}  
                    sakType: ${saksInfoSamlet?.saktype?.name}, 
                    identifisertPerson aktoerId: ${identifisertPerson?.aktoerId} 
                **********""".trimIndent()
                )

                val aktoerId = identifisertPerson?.aktoerId

                val tildeltJoarkEnhet = journalforingsEnhet(
                    fdato,
                    identifisertPerson,
                    sedHendelse,
                    hendelseType,
                    saksInfoSamlet,
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
                val tema = hentTema(
                    sedHendelse,
                    identifisertPerson?.personRelasjon?.fnr,
                    identifisertePersoner,
                    kravTypeFraSed,
                    saksInfoSamlet
                ).also {
                    logger.info("Hent tema gir: $it for ${sedHendelse.rinaSakId}, sedtype: ${sedHendelse.sedType}, buc: ${sedHendelse.bucType}")
                }

                // TODO: sende inn saksbehandlerInfo kun dersom det trengs til metoden under.
                // Oppretter journalpost

                val journalPostResponseOgRequest = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = tildeltJoarkEnhet,
                    arkivsaksnummer = hentSak(sedHendelse.rinaSakId, saksInfoSamlet?.saksIdFraSed, saksInfoSamlet?.sakInformasjon),
                    dokumenter = documents,
                    saktype = saksInfoSamlet?.saktype,
                    institusjon = institusjon,
                    identifisertePersoner = identifisertePersoner,
                    saksbehandlerInfo = navAnsattInfo,
                    tema = tema,
                    kravType = kravTypeFraSed
                )

                val journalPostResponse = journalPostResponseOgRequest.first

                // Dette er en ny feature som ser om vi mangler bruker, eller om det er tidligere sed/journalposter på samme buc som har manglet
                if(journalPostResponseOgRequest.second.bruker == null){
                    logger.info("Journalposten mangler bruker og vil bli lagret for fremtidig vurdering")
                    gcpStorageService.lagreJournalPostRequest(
                        journalPostResponseOgRequest.first?.journalpostId,
                        sedHendelse.rinaSakId,
                        sedHendelse.sedId
                    )
                } else {
                    // ser om vi har lagret sed fra samme buc. Hvis ja; se om vi har bruker vi kan benytte i lagret sedhendelse
                    try {
                        gcpStorageService.arkiverteSakerForRinaId(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)?.forEach { rinaId ->
                            logger.info("Henter tidligere journalføring for å sette bruker for sed: $rinaId")
                            gcpStorageService.hentOpprettJournalpostRequest(rinaId)?.let { journalpostId ->
                                val innhentetJournalpost = safClient.hentJournalpost(journalpostId.first)

                                logger.info("Hentet journalpost: ${innhentetJournalpost?.journalpostId} med status: ${innhentetJournalpost?.journalstatus}")
                                if (innhentetJournalpost?.journalstatus  == UNDER_ARBEID) {
                                    //oppdaterer oppgave med status, enhet og tema
                                    OppgaveMelding(
                                        sedHendelse.sedType,
                                        journalpostId.first,
                                        journalPostResponseOgRequest.second.journalfoerendeEnhet!!,
                                        aktoerId,
                                        sedHendelse.rinaSakId,
                                        hendelseType,
                                        null,
                                        if (hendelseType == MOTTATT) OppgaveType.JOURNALFORING else OppgaveType.JOURNALFORING_UT,
                                        tema = tema
                                    ).also { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(it) }.also { secureLog.info("Oppdatert oppgave ${it}") }
                                    val journalpostrequest = journalpostService.oppdaterJournalpost(
                                        innhentetJournalpost,
                                        journalPostResponseOgRequest.second.bruker!!,
                                        journalPostResponseOgRequest.second.tema,
                                        journalPostResponseOgRequest.second.journalfoerendeEnhet!!,
                                        journalPostResponseOgRequest.second.behandlingstema ?: innhentetJournalpost.behandlingstema!!
                                    ).also { logger.info("Oppdatert journalpost med JPID: ${journalpostId.first}") }
                                    secureLog.info("""Henter opprettjournalpostRequest:
                                        | ${journalpostrequest.toJson()}   
                                        | ${journalPostResponseOgRequest.second.bruker!!.toJson()}""".trimMargin()
                                    )
                                }

                                gcpStorageService.slettJournalpostDetaljer(journalpostId.second)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Det har skjedd feil med henting av arkivert saker")
                    }
                }


                // journalposten skal settes til avbrutt KUN VED UTGÅENDE SEDer ved manglende bruker/identifisertperson
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
                        if (hendelseType == MOTTATT) OppgaveType.JOURNALFORING else OppgaveType.JOURNALFORING_UT,
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
                        saksInfoSamlet?.sakInformasjon,
                        sed
                    )
                } else loggDersomIkkeBehSedOppgaveOpprettes(sedHendelse.bucType, sedHendelse, journalPostResponse.journalpostferdigstilt, hendelseType)

                produserStatistikkmelding(
                    sedHendelse,
                    tildeltJoarkEnhet.enhetsNr,
                    saksInfoSamlet?.saktype,
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
        sedHendelse: SedHendelse,
        hendelseType: HendelseType,
        sakInfo: SaksInfoSamlet?,
        harAdressebeskyttelse: Boolean,
        antallIdentifisertePersoner: Int,
        kravTypeFraSed: KravType?
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
                    sakInfo?.saktype,
                    sedHendelse,
                    hendelseType,
                    sakInfo?.sakInformasjon,
                    harAdressebeskyttelse
                )
            )

            if (enhetFraRouting != ID_OG_FORDELING) return enhetFraRouting.also { logEnhet(enhetFraRouting, it) }

            else if(bucType in listOf(P_BUC_05, P_BUC_06)) return enhetDersomIdOgFordeling(identifisertPerson, fdato, antallIdentifisertePersoner).also { logEnhet(enhetFraRouting, it) }

            else return enhetBasertPaaBehandlingstema(
                sedHendelse,
                sakInfo,
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
        sakinfo: SaksInfoSamlet?,
        identifisertPerson: IdentifisertPerson,
        antallIdentifisertePersoner: Int,
        kravtypeFraSed: KravType?
    ): Enhet {
        val tema = hentTema(sedHendelse, identifisertPerson.fnr, antallIdentifisertePersoner, null, sakinfo)
        val behandlingstema = journalpostService.bestemBehandlingsTema(
            sedHendelse?.bucType!!,
            sakinfo?.saktype,
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
        fnr: Fodselsnummer?,
        identifisertePersoner: Int,
        kravtypeFraSed: KravType?,
        saksInfo: SaksInfoSamlet?
    ): Tema {
        if(fnr == null) {
            // && saktype != UFOREP && sedhendelse?.bucType != P_BUC_03 && sedhendelse?.sedType != SedType.P2200 || kravtypeFraSed != KravType.UFOREP) return PENSJON
            if(sedhendelse?.bucType == P_BUC_03 || saksInfo?.saktype == UFOREP || kravtypeFraSed == KravType.UFOREP) return UFORETRYGD
            return PENSJON
        }
        if (sedhendelse?.rinaSakId != null && gcpStorageService.gjennyFinnes(sedhendelse.rinaSakId)) {
            val blob = gcpStorageService.hentFraGjenny(sedhendelse.rinaSakId)
            return if (blob?.contains("BARNEP") == true) EYBARNEP else OMSTILLING
        }

        //https://confluence.adeo.no/pages/viewpage.action?pageId=603358663
        return when (sedhendelse?.bucType) {

            P_BUC_01, P_BUC_02 -> if (identifisertePersoner == 1 && saksInfo?.saktype == UFOREP || identifisertePersoner == 1 && erUforAlderUnder62(fnr)) UFORETRYGD else PENSJON
            P_BUC_03 -> UFORETRYGD
            P_BUC_04, P_BUC_05, P_BUC_07, P_BUC_09, P_BUC_06, P_BUC_08 ->
                if (identifisertePersoner == 1 && erUforAlderUnder62(fnr) || saksInfo?.saktype == UFOREP) UFORETRYGD else PENSJON
            P_BUC_10 -> if (kravtypeFraSed == KravType.UFOREP && saksInfo?.sakInformasjon?.sakStatus == SakStatus.LOPENDE|| erUforAlderUnder62(fnr) && identifisertePersoner == 1) UFORETRYGD else PENSJON
            else -> if (saksInfo?.saktype == UFOREP && erUforAlderUnder62(fnr)) UFORETRYGD else PENSJON
        }
    }

    fun erUforAlderUnder62(fnr: Fodselsnummer?) = Period.between(fnr?.getBirthDate(), LocalDate.now()).years in 18..61

    fun hentSak(
        euxCaseId: String,
        sakIdFraSed: String? = null,
        sakInformasjon: SakInformasjon? = null
    ): Sak? {
        return if (gcpStorageService.gjennyFinnes(euxCaseId) && sakIdFraSed != null)
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
