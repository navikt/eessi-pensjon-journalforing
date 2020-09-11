package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.pesys.SakInformasjon
import no.nav.eessi.pensjon.klienter.pesys.SakStatus
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType.P_BUC_02
import no.nav.eessi.pensjon.models.BucType.R_BUC_02
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
import javax.annotation.PostConstruct

@Service
class JournalforingService(private val euxKlient: EuxKlient,
                           private val journalpostKlient: JournalpostKlient,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService,
                           private val oppgaveHandler: OppgaveHandler,
                           @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private lateinit var journalforOgOpprettOppgaveForSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        journalforOgOpprettOppgaveForSed = metricsHelper.init("journalforOgOpprettOppgaveForSed")
    }

    fun journalfor(sedHendelse: SedHendelseModel,
                   hendelseType: HendelseType,
                   identifisertPerson: IdentifisertPerson?,
                   fdato: LocalDate,
                   ytelseType: YtelseType?,
                   offset: Long = 0,
                   sakInformasjon: SakInformasjon?) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")
                if (sakInformasjon != null) {
                    logger.info("kafka offset: $offset, hentSak PESYS saknr: $sakInformasjon på aktoerid: ${identifisertPerson?.aktoerId} og rinaid: ${sedHendelse.rinaSakId}")
                }

                // Henter dokumenter
                val sedDokumenterJSON = euxKlient.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedType!!)

                val tildeltEnhet =  tildeltEnhet(sakInformasjon, sedHendelse, hendelseType, identifisertPerson, fdato, ytelseType)

                val forsokFerdigstill = forsokFerdigstill(tildeltEnhet)

                // Oppretter journalpost
                val journalPostResponse = journalpostKlient.opprettJournalpost(
                    rinaSakId = sedHendelse.rinaSakId,
                    fnr = identifisertPerson?.personRelasjon?.fnr,
                    personNavn = identifisertPerson?.personNavn,
                    bucType = sedHendelse.bucType.name,
                    sedType = sedHendelse.sedType.name,
                    sedHendelseType = hendelseType.name,
                    eksternReferanseId = null,// TODO what value to put here?,
                    kanal = "EESSI",
                    journalfoerendeEnhet = tildeltEnhet.enhetsNr,
                    arkivsaksnummer = sakInformasjon?.sakId,
                    dokumenter = documents,
                    forsokFerdigstill = forsokFerdigstill,
                    avsenderLand = sedHendelse.avsenderLand,
                    avsenderNavn = sedHendelse.avsenderNavn,
                    ytelseType = ytelseType
                )

                // Oppdaterer distribusjonsinfo
                if (forsokFerdigstill) {
                    journalpostKlient.oppdaterDistribusjonsinfo(journalPostResponse!!.journalpostId)
                }

                if (!journalPostResponse!!.journalpostferdigstilt) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, journalPostResponse.journalpostId, tildeltEnhet, identifisertPerson?.aktoerId, "JOURNALFORING", sedHendelse, hendelseType)
                }

                if (uSupporterteVedlegg.isNotEmpty()) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, null, tildeltEnhet, identifisertPerson?.aktoerId, "BEHANDLE_SED", sedHendelse, hendelseType, usupporterteFilnavn(uSupporterteVedlegg))
                }

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

    private fun forsokFerdigstill(tildeltEnhet: OppgaveRoutingModel.Enhet): Boolean {
        return tildeltEnhet == OppgaveRoutingModel.Enhet.AUTOMATISK_JOURNALFORING
    }

    fun tildeltEnhet(sakInformasjon: SakInformasjon?, sedHendelse: SedHendelseModel, hendelseType: HendelseType, identifisertPerson: IdentifisertPerson?, fdato: LocalDate, ytelseType: YtelseType?): OppgaveRoutingModel.Enhet {
        val tildeltEnhet =
                if (tildeltEnhetRegel(sakInformasjon, sedHendelse, hendelseType, identifisertPerson)) {
                    oppgaveRoutingService.route(OppgaveRoutingRequest(identifisertPerson?.aktoerId,
                            fdato,
                            identifisertPerson?.diskresjonskode,
                            identifisertPerson?.landkode,
                            identifisertPerson?.geografiskTilknytning,
                            sedHendelse.bucType,
                            ytelseType,
                            sedHendelse.sedType,
                            hendelseType,
                            sakInformasjon?.sakStatus,
                            identifisertPerson)
                    )
                } else {
                    OppgaveRoutingModel.Enhet.AUTOMATISK_JOURNALFORING
                }
        return tildeltEnhet
    }


    fun tildeltEnhetRegel(sakInformasjon: SakInformasjon?, sedHendelse: SedHendelseModel, hendelseType: HendelseType, identifisertPerson: IdentifisertPerson?): Boolean {
        if (sakInformasjon == null) return true

        return when {
            sedHendelse.bucType == R_BUC_02 && hendelseType == SENDT && identifisertPerson != null && identifisertPerson.flereEnnEnPerson() -> true
            sedHendelse.bucType == P_BUC_02 && hendelseType == SENDT && sakInformasjon.sakType == YtelseType.UFOREP && sakInformasjon.sakStatus == SakStatus.AVSLUTTET -> true
            sedHendelse.bucType == P_BUC_02 && hendelseType == MOTTATT -> true
            else -> false

        }
    }

    private fun publishOppgavemeldingPaaKafkaTopic(sedType: SedType, journalpostId: String?, tildeltEnhet: OppgaveRoutingModel.Enhet, aktoerId: String?, oppgaveType: String, sedHendelse: SedHendelseModel, hendelseType: HendelseType, filnavn: String? = null) {
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(OppgaveMelding(
                sedType = sedType.name,
                journalpostId = journalpostId,
                tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                aktoerId = aktoerId,
                oppgaveType = oppgaveType,
                rinaSakId = sedHendelse.rinaSakId,
                hendelseType = hendelseType.name,
                filnavn = filnavn
        ))
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<EuxDokument>): String {
        var filnavn = ""
        uSupporterteVedlegg.forEach { vedlegg -> filnavn += vedlegg.filnavn + " " }
        return filnavn
    }
}
