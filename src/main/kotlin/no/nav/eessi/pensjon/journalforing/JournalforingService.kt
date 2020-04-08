package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

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

    fun journalfor(sedHendelse: SedHendelseModel,
                   hendelseType: HendelseType,
                   identifisertPerson: IdentifisertPerson,
                   offset: Long = 0) {
        metricsHelper.measure("journalforOgOpprettOppgaveForSed") {
            try {
                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

                val ytelseType = hentYtelseKravType(sedHendelse)

                // Henter dokumenter
                val sedDokumenterJSON = euxKlient.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedType!!)

                // Henter saksId for utgående dokumenter
                val sakId=
                        if (identifisertPerson.aktoerId != null && hendelseType == HendelseType.SENDT) {
                            bestemSakKlient.hentSakId(identifisertPerson.aktoerId, sedHendelse.bucType!!)
                        } else { null }

                if (sakId != null) {
                    logger.info("kafka offset: $offset, hentSak PESYS saknr: $sakId på aktoerid: ${identifisertPerson.aktoerId} og rinaid: ${sedHendelse.rinaSakId}")
                }

                val forsokFerdigstill = sakId != null

                val tildeltEnhet =
                        if (sakId == null) {
                            oppgaveRoutingService.route(OppgaveRoutingRequest(identifisertPerson.fnr,
                                    identifisertPerson.fdato,
                                    identifisertPerson.diskresjonskode,
                                    identifisertPerson.landkode,
                                    identifisertPerson.geografiskTilknytning,
                                    sedHendelse.bucType,
                                    ytelseType)
                            )
                        } else {
                            OppgaveRoutingModel.Enhet.UKJENT
                        }

                // Oppretter journalpost
                val journalPostResponse = journalpostKlient.opprettJournalpost(
                        rinaSakId = sedHendelse.rinaSakId,
                        fnr = identifisertPerson.fnr,
                        personNavn = identifisertPerson.personNavn,
                        bucType = sedHendelse.bucType!!.name,
                        sedType = sedHendelse.sedType.name,
                        sedHendelseType = hendelseType.name,
                        eksternReferanseId = null,// TODO what value to put here?,
                        kanal = "EESSI",
                        journalfoerendeEnhet = tildeltEnhet.enhetsNr,
                        arkivsaksnummer = sakId,
                        dokumenter = documents,
                        forsokFerdigstill = forsokFerdigstill,
                        avsenderLand = sedHendelse.avsenderLand,
                        avsenderNavn = sedHendelse.avsenderNavn
                )

                // Oppdaterer distribusjonsinfo
                if (sakId != null) {
                    journalpostKlient.oppdaterDistribusjonsinfo(journalPostResponse!!.journalpostId)
                }

                if (!journalPostResponse!!.journalpostferdigstilt) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, journalPostResponse.journalpostId, tildeltEnhet, identifisertPerson.aktoerId, "JOURNALFORING", sedHendelse, hendelseType)
                }

                if (uSupporterteVedlegg.isNotEmpty()) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, null, tildeltEnhet, identifisertPerson.aktoerId, "BEHANDLE_SED", sedHendelse, hendelseType, usupporterteFilnavn(uSupporterteVedlegg))
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

    private fun hentYtelseKravType(sedHendelse: SedHendelseModel): String? {
        if (sedHendelse.sedType == SedType.P2100 || sedHendelse.sedType == SedType.P15000) {
            return try {
                fagmodulKlient.hentYtelseKravType(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            } catch (ex: Exception) {
                null
            }
        }
        return null
    }
}
