package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.automatisering.AutomatiseringMelding
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
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
import no.nav.eessi.pensjon.models.Behandlingstema.ALDERSPENSJON
import no.nav.eessi.pensjon.models.Behandlingstema.BARNEP
import no.nav.eessi.pensjon.models.Behandlingstema.GJENLEVENDEPENSJON
import no.nav.eessi.pensjon.models.Behandlingstema.UFOREPENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.oppgaverouting.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.oppgaverouting.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
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
        harAdressebeskyttelse: Boolean = false
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

                val tildeltEnhet = if (fdato == null ||  fdato != identifisertPerson?.personRelasjon?.fnr?.getBirthDate()) {
                    logger.info("Fdato er forskjellig fra SED fnr, sender til $ID_OG_FORDELING fdato: $fdato identifisertpersonfnr: ${identifisertPerson?.personRelasjon?.fnr?.getBirthDate()}")
                    ID_OG_FORDELING
                } else {
                    oppgaveRoutingService.hentEnhet(
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
                }

                val arkivsaksnummer = sakInformasjon?.sakId.takeIf { tildeltEnhet == AUTOMATISK_JOURNALFORING }

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
                    journalfoerendeEnhet = tildeltEnhet,
                    arkivsaksnummer = arkivsaksnummer,
                    dokumenter = documents,
                    avsenderLand = sedHendelse.avsenderLand,
                    avsenderNavn = sedHendelse.avsenderNavn,
                    saktype = saktype,
                    institusjon = institusjon
                )

                // Oppdaterer distribusjonsinfo for utgående og automatisk journalføring (Ferdigstiller journalposten)
                if (tildeltEnhet == AUTOMATISK_JOURNALFORING && hendelseType == HendelseType.SENDT) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse!!.journalpostId)
                }
                val aktoerId = identifisertPerson?.aktoerId

                if (!journalPostResponse!!.journalpostferdigstilt) {
                    val melding = OppgaveMelding(
                        sedHendelse.sedType,
                        journalPostResponse.journalpostId,
                        tildeltEnhet,
                        aktoerId,
                        sedHendelse.rinaSakId,
                        hendelseType,
                        null,
                        OppgaveType.JOURNALFORING
                    )
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                }

                //midlertidig for å teste på enhet og forskjellige verdier
                tildeltOppgaveEnhet(enhet = tildeltEnhet, behandlingstema = journalpostService.bestemBehandlingsTema(sedHendelse.bucType!!, saktype), identifisertePerson = identifisertPerson ).also {
                    logger.info("Journalføring: enhet: $it hentet fra behandlingstema")
                }

                val oppgaveEnhet =  hentOppgaveEnhet(
                    tildeltEnhet,
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
                if ((bucType == P_BUC_01 || bucType == P_BUC_03) && (hendelseType == HendelseType.MOTTATT && tildeltEnhet == AUTOMATISK_JOURNALFORING && journalPostResponse.journalpostferdigstilt)) {
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

                produserAutomatiseringsmelding(sedHendelse.rinaSakId,
                    sedHendelse.rinaDokumentId,
                    sedHendelse.rinaDokumentVersjon,
                    java.time.LocalDateTime.now(),
                    tildeltEnhet == AUTOMATISK_JOURNALFORING,
                    tildeltEnhet.enhetsNr,
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

    private fun tildeltOppgaveEnhet(enhet:Enhet, behandlingstema:Behandlingstema?, identifisertePerson: IdentifisertPerson?): Any? {

        try {
            logger.info("landkode: ${identifisertePerson?.landkode} og behandlingstema: $behandlingstema og enhet:$enhet")
            if (enhet in listOf(ID_OG_FORDELING, AUTOMATISK_JOURNALFORING)) {
                return if (identifisertePerson?.landkode == "NOR" ) {
                    when (behandlingstema) {
                        GJENLEVENDEPENSJON, BARNEP -> NFP_UTLAND_AALESUND
                        ALDERSPENSJON -> NFP_UTLAND_AALESUND
                        UFOREPENSJON -> UFORE_UTLANDSTILSNITT
                        else -> logger.warn("Finner ingen verdi for behandlingstema")
                    }
                } else when (behandlingstema!!) {
                    UFOREPENSJON -> UFORE_UTLANDSTILSNITT
                    GJENLEVENDEPENSJON, BARNEP, ALDERSPENSJON -> PENSJON_UTLAND
                    else -> logger.warn("Finner ingen verdi for behandlingstema")
                }
            }
            return enhet
        }
        catch (ex :Exception) {
            logger.warn("Feil under testing av enhet ${ex.stackTrace}")
        }
        return null
    }

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
