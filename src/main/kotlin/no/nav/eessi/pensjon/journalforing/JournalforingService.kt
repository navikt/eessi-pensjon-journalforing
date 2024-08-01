package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.google.cloud.storage.BlobId
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
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
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
import no.nav.eessi.pensjon.utils.mapJsonToAny
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
    private val vurderBrukerInfo: VurderBrukerInfo,
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

                val tema = hentTema(
                    sedHendelse,
                    identifisertPerson?.personRelasjon?.fnr,
                    identifisertePersoner,
                    kravTypeFraSed,
                    saksInfoSamlet
                ).also {
                    logger.info("Hent tema gir: $it for ${sedHendelse.rinaSakId}, sedtype: ${sedHendelse.sedType}, buc: ${sedHendelse.bucType}")
                }

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
                                        usupporterteFilnavn(documentsAndAttachments.second),
                                        tema
                                    )
                                }
                            }
                    } ?: throw IllegalStateException("sedType is null")
                }

                val institusjon = avsenderMottaker(hendelseType, sedHendelse)

                // TODO: sende inn saksbehandlerInfo kun dersom det trengs til metoden under.

                val arkivsaksnummer = hentSak(
                    sedHendelse.rinaSakId,
                    saksInfoSamlet?.saksIdFraSed,
                    saksInfoSamlet?.sakInformasjon,
                    identifisertPerson?.personRelasjon?.fnr
                ).also { logger.info("""SakId for rinaSak: ${sedHendelse.rinaSakId}: 
                    | $it""".trimMargin()) }

                // Oppretter journalpost

                val journalpostRequest = journalpostService.opprettJournalpost(
                    sedHendelse = sedHendelse,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = tildeltJoarkEnhet,
                    arkivsaksnummer = arkivsaksnummer,
                    dokumenter = documents,
                    saktype = saksInfoSamlet?.saktype,
                    institusjon = institusjon,
                    identifisertePersoner = identifisertePersoner,
                    saksbehandlerInfo = navAnsattInfo,
                    tema = tema,
                    kravType = kravTypeFraSed
                )

                if(journalpostRequest.bruker == null){
                    vurderBrukerInfo.journalPostUtenBruker(
                        journalpostRequest,
                        sedHendelse,
                        hendelseType)
                    logger.warn("Journalpost er satt på vent grunnet manglende bruker, ringnr: ${sedHendelse.rinaSakId}")
                    return@measure
                }
                else {
                    val journalPostResponse = journalpostService.sendJournalPost(
                            journalpostRequest,
                            sedHendelse,
                            hendelseType,
                            navAnsattInfo?.first
                    )

                    vurderBrukerInfo.journalpostMedBruker(
                        journalpostRequest,
                        sedHendelse,
                        identifisertPerson,
                        journalpostRequest.bruker,
                        navAnsattInfo?.first)

                    // journalposten skal settes til avbrutt KUN VED UTGÅENDE SEDer ved manglende bruker/identifisertperson
                    val kanLageOppgave = journalpostService.skalStatusSettesTilAvbrutt(
                        identifisertPerson?.personRelasjon?.fnr,
                        hendelseType,
                        sedHendelse,
                        journalPostResponse,
                    )

                    // Oppdaterer distribusjonsinfo for utgående og maskinell journalføring (Ferdigstiller journalposten)
                    if (journalPostResponse?.journalpostferdigstilt == true && hendelseType == HendelseType.SENDT) {
                        journalpostService.oppdaterDistribusjonsinfo(journalPostResponse.journalpostId)
                    }

                    journalPostResponse?.journalpostferdigstilt.let { journalPostFerdig ->
                        logger.info("Maskinelt journalført: $journalPostFerdig, sed: ${sedHendelse.sedType}, enhet: $tildeltJoarkEnhet, sattavbrutt: $kanLageOppgave **********")
                    }

                    if (journalPostResponse?.journalpostferdigstilt == false && !kanLageOppgave) {
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
                    if ((hendelseType == MOTTATT && journalPostResponse?.journalpostferdigstilt!!)) {
                        logger.info("Oppretter BehandleOppgave til bucType: ${sedHendelse.bucType}")
                        opprettBehandleSedOppgave(
                            journalPostResponse.journalpostId,
                            tildeltJoarkEnhet,
                            aktoerId,
                            sedHendelse,
                            tema = tema
                        )
                        kravInitialiseringsService.initKrav(
                            sedHendelse,
                            saksInfoSamlet?.sakInformasjon,
                            sed
                        )
                    } else loggDersomIkkeBehSedOppgaveOpprettes(
                        sedHendelse.bucType,
                        sedHendelse,
                        journalPostResponse?.journalpostferdigstilt,
                        hendelseType
                    )

                    produserStatistikkmelding(
                        sedHendelse,
                        tildeltJoarkEnhet.enhetsNr,
                        saksInfoSamlet?.saktype,
                        hendelseType
                    )
                }
            } catch (ex: MismatchedInputException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Det oppstod en uventet feil ved journalforing av hendelse", ex)
                throw ex
            }
        }
    }

    fun lagJournalpostOgOppgave(journalpostRequest: LagretJournalpostMedSedInfo, saksbehandlerIdent: String? = null, blobId: BlobId){
        val response = journalpostService.sendJournalPost(journalpostRequest, "eessipensjon")

        logger.info("""Lagret JP hentet fra GCP: 
                | sedHendelse: ${journalpostRequest.sedHendelse}
                | enhet: ${journalpostRequest.journalpostRequest.journalfoerendeEnhet}
                | tema: ${journalpostRequest.journalpostRequest.tema}""".trimMargin()
        )

        if(response?.journalpostId != null) {
            val melding = OppgaveMelding(
                sedType = journalpostRequest.sedHendelse.sedType,
                journalpostId = response.journalpostId,
                tildeltEnhetsnr = journalpostRequest.journalpostRequest.journalfoerendeEnhet!!,
                aktoerId = null,
                rinaSakId = journalpostRequest.sedHendelse.rinaSakId,
                hendelseType = journalpostRequest.sedHendelseType,
                filnavn = null,
                oppgaveType = OppgaveType.JOURNALFORING,
            ).also { oppgaveMelding ->  logger.info("Opprettet journalforingsoppgave for sak med rinaId: ${oppgaveMelding.rinaSakId}") }
            oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
            gcpStorageService.slettJournalpostDetaljer(blobId)
        } else {
            logger.error("Journalpost ikke opprettet")
        }
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


    /**
     * Henter en sak basert på sakid og/eller gjenny informasjon
     *
     * @param euxCaseId SaksID fra EUX.
     * @param sakIdFraSed SaksID fra SED (valgfri).
     * @param sakInformasjon Tilleggsinfo om saken (valgfri).
     * @param identifisertPersonFnr Fødselsnummer (valgfri).
     * @return Et `Sak`-objekt hvis:
     * 1. saken finnes i Gjenny.
     * 2. identifisert person fnr  eksisterer.
     * 3. det finnes gyldig Pesys-nummer i `sakInformasjon` eller `sakIdFraSed`.
     */
    fun hentSak(
        euxCaseId: String,
        sakIdFraSed: String? = null,
        sakInformasjon: SakInformasjon? = null,
        identifisertPersonFnr: Fodselsnummer? = null
    ): Sak? {

        if(euxCaseId == sakIdFraSed || euxCaseId == sakInformasjon?.sakId){
            logger.error("SakIdFraSed: $sakIdFraSed eller sakId fra saksInformasjon: ${sakInformasjon?.sakId} er lik rinaSakId: $euxCaseId")
            return null
        }

        // 1. Er dette en Gjenny sak
        if (gcpStorageService.gjennyFinnes(euxCaseId)) {
            val gjennySak = gcpStorageService.hentFraGjenny(euxCaseId)?.let { mapJsonToAny<GjennySak>(it) }
            return gjennySak?.sakId?.let { Sak("FAGSAK", it, "EY") }
        }

        // 2. Joark oppretter ikke JP der det finnes sak, men mangler bruker
        if(identifisertPersonFnr == null){
            logger.warn("Fnr mangler for rinaSakId: $euxCaseId, henter derfor ikke sak")
            return null
        }

        // 3. Pesys nr fra pesys
        sakInformasjon?.sakId?.takeIf { it.isNotBlank() &&  it.erGyldigPesysNummer() }?.let {
            return Sak("FAGSAK", it, "PP01").also { logger.info("Har funnet saksinformasjon fra pesys: $it, saksType:${sakInformasjon.sakType}, sakStatus:${sakInformasjon.sakStatus}") }
        }

        // 4. Pesys nr fra SED
        sakIdFraSed?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let {
            return Sak("FAGSAK", it, "PP01").also { logger.info("har funnet saksinformasjon fra SED: $it") }
        }

        logger.warn("""RinaID: $euxCaseId
            | sakIdFraSed: $sakIdFraSed eller sakId fra saksInformasjon: ${sakInformasjon?.sakId}
            | mangler verdi eller er ikke gyldig pesys nummer""".trimMargin())
        return null
    }

    /**
     * @return true om første tall er 1 eller 2 (pesys saksid begynner på 1 eller 2) og at lengden er 8 siffer
     */
    private fun String.erGyldigPesysNummer(): Boolean {
        return (this.first() == '1' || this.first() == '2') && this.length == 8
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
        uSupporterteVedlegg: String? = null,
        tema: Tema
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
                tema
            )
            oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(oppgave)
        } else logger.warn("Nå har du forsøkt å opprette en BEHANDLE_SED oppgave, men avsenderland er Norge.")
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<SedVedlegg>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }
}
