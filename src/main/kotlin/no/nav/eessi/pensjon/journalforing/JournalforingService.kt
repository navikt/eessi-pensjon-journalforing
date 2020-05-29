package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
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
                           private val fagmodulKlient: FagmodulKlient,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService,
                           private val oppgaveHandler: OppgaveHandler,
                           private val bestemSakKlient: BestemSakKlient,
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
                   offset: Long = 0) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

                //val ytelseType = hentYtelseKravType(sedHendelse)  //TODO kan fjernes da denne er nå høyere opp

                // Henter dokumenter
                val sedDokumenterJSON = euxKlient.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedType!!)

                // Henter saksId for utgående dokumenter
                val sakId=
                        if (identifisertPerson?.aktoerId != null && hendelseType == HendelseType.SENDT) {
                            bestemSakKlient.hentSakId(identifisertPerson.aktoerId, sedHendelse.bucType, ytelseType)
                        } else { null }

                if (sakId != null) {
                    logger.info("kafka offset: $offset, hentSak PESYS saknr: $sakId på aktoerid: ${identifisertPerson?.aktoerId} og rinaid: ${sedHendelse.rinaSakId}")
                }

                val forsokFerdigstill = sakId != null

                val tildeltEnhet =
                        if (sakId == null) {
                            oppgaveRoutingService.route(OppgaveRoutingRequest(identifisertPerson?.aktoerId,
                                    fdato,
                                    identifisertPerson?.diskresjonskode,
                                    identifisertPerson?.landkode,
                                    identifisertPerson?.geografiskTilknytning,
                                    sedHendelse.bucType,
                                    ytelseType,
                                    sedHendelse.sedType,
                                    hendelseType)
                    )
                } else {
                    OppgaveRoutingModel.Enhet.UKJENT
                        }

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
                    arkivsaksnummer = sakId,
                    dokumenter = documents,
                    forsokFerdigstill = forsokFerdigstill,
                    avsenderLand = sedHendelse.avsenderLand,
                    avsenderNavn = sedHendelse.avsenderNavn,
                    ytelseType = ytelseType
                )

                // Oppdaterer distribusjonsinfo
                if (sakId != null) {
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
