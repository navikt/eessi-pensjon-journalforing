package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import javax.annotation.PostConstruct

@Service
class JournalforingService(
    private val journalpostService: JournalpostService,
    private val oppgaveRoutingService: OppgaveRoutingService,
    private val pdfService: PDFService,
    private val oppgaveHandler: OppgaveHandler,
    private val kravInitialiseringsService: KravInitialiseringsService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()),
) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private lateinit var journalforOgOpprettOppgaveForSed: MetricsHelper.Metric

    @Value("\${namespace}")
    lateinit var nameSpace: String

    @PostConstruct
    fun initMetrics() {
        journalforOgOpprettOppgaveForSed = metricsHelper.init("journalforOgOpprettOppgaveForSed")
    }

    fun journalfor(
        sedHendelseModel: SedHendelseModel,
        hendelseType: HendelseType,
        identifisertPerson: IdentifisertPerson?,
        fdato: LocalDate?,
        saktype: Saktype?,
        offset: Long,
        sakInformasjon: SakInformasjon?
    ) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info(
                    """**********
                    rinadokumentID: ${sedHendelseModel.rinaDokumentId} rinasakID: ${sedHendelseModel.rinaSakId} sedType: ${sedHendelseModel.sedType?.name} bucType: ${sedHendelseModel.bucType}
                    hendelseType: $hendelseType, kafka offset: $offset, hentSak sakId: ${sakInformasjon?.sakId} sakType: ${sakInformasjon?.sakType} på aktoerId: ${identifisertPerson?.aktoerId} sakType: $saktype
                **********""".trimIndent()
                )

                // Henter dokumenter
                val (documents, uSupporterteVedlegg) = sedHendelseModel.run {
                    pdfService.hentDokumenterOgVedlegg(rinaSakId, rinaDokumentId, sedType!!)
                }

                val tildeltEnhet = if (fdato == null) {
                    Enhet.ID_OG_FORDELING
                } else {
                    oppgaveRoutingService.route(
                        OppgaveRoutingRequest.fra(
                            identifisertPerson,
                            fdato,
                            saktype,
                            sedHendelseModel,
                            hendelseType,
                            sakInformasjon
                        )
                    )
                }

                val arkivsaksnummer = sakInformasjon?.sakId.takeIf { tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING }

                // Oppretter journalpost
                val journalPostResponse = journalpostService.opprettJournalpost(
                    rinaSakId = sedHendelseModel.rinaSakId,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    bucType = sedHendelseModel.bucType!!,
                    sedType = sedHendelseModel.sedType!!,
                    sedHendelseType = hendelseType,
                    journalfoerendeEnhet = tildeltEnhet,
                    arkivsaksnummer = arkivsaksnummer,
                    dokumenter = documents,
                    avsenderLand = sedHendelseModel.avsenderLand,
                    avsenderNavn = sedHendelseModel.avsenderNavn,
                    saktype = saktype
                )

                // Oppdaterer distribusjonsinfo
                if (tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING && hendelseType == HendelseType.SENDT) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse!!.journalpostId)
                }
                val aktoerId = identifisertPerson?.aktoerId

                if (!journalPostResponse!!.journalpostferdigstilt) {
                    val melding = OppgaveMelding(
                        sedHendelseModel.sedType,
                        journalPostResponse.journalpostId,
                        tildeltEnhet,
                        aktoerId,
                        sedHendelseModel.rinaSakId,
                        hendelseType,
                        null,
                        OppgaveType.JOURNALFORING
                    )
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                }

                val oppgaveEnhet = hentOppgaveEnhet(
                    tildeltEnhet,
                    identifisertPerson,
                    fdato,
                    saktype,
                    sedHendelseModel
                )

                if (uSupporterteVedlegg.isNotEmpty()) {
                    opprettBehandleSedOppgave(
                        null,
                        oppgaveEnhet,
                        aktoerId,
                        sedHendelseModel,
                        usupporterteFilnavn(uSupporterteVedlegg)
                    )
                }

                kravInitialiseringsService.fixKRav(sedHendelseModel, hendelseType, tildeltEnhet, journalPostResponse, sakInformasjon, oppgaveEnhet, aktoerId)

            } catch (ex: MismatchedInputException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: MissingKotlinParameterException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Det oppstod en uventet feil ved journalforing av hendelse", ex)
                throw ex
            }
        }
    }

    private fun opprettBehandleSedOppgave(
        journalpostId: String? = null,
        oppgaveEnhet: Enhet,
        aktoerId: String? = null,
        sedHendelseModel: SedHendelseModel,
        uSupporterteVedlegg: String? = null
    ) {
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
    }

    private fun hentOppgaveEnhet(
        tildeltEnhet: Enhet,
        identifisertPerson: IdentifisertPerson?,
        fdato: LocalDate?,
        saktype: Saktype?,
        sedHendelseModel: SedHendelseModel,
    ): Enhet {
        return if (tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING) {
            oppgaveRoutingService.route(
                OppgaveRoutingRequest.fra(
                    identifisertPerson,
                    fdato!!,
                    saktype,
                    sedHendelseModel,
                    HendelseType.MOTTATT,
                    null
                )
            )
        } else
            tildeltEnhet
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<SedVedlegg>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }
}
