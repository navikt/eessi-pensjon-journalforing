package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.services.pesys.PenService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class JournalforingService(private val euxService: EuxService,
                           private val journalpostService: JournalpostService,
                           private val fagmodulService: FagmodulService,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService,
                           private val oppgaveHandler: OppgaveHandler,
                           private val penService: PenService,
                           @Value("\${NAIS_NAMESPACE}") private val namespace: String,
                           @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()))  {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    fun journalfor(sedHendelse: SedHendelseModel,
                   hendelseType: HendelseType,
                   identifisertPerson: IdentifisertPerson) {
        metricsHelper.measure("journalforOgOpprettOppgaveForSed") {
            try {
                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

                // TODO pin og ytelse skal gjøres om til å returnere kun ytelse
                val pinOgYtelse = hentYtelseKravType(sedHendelse)

                val sedDokumenterJSON = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedType!!)

                val tildeltEnhet = hentTildeltEnhet(
                        sedHendelse.sedType,
                        sedHendelse.bucType!!,
                        pinOgYtelse,
                        identifisertPerson
                )

                var sakId: String? = null
                if(identifisertPerson.aktoerId != null) {
                    if (hendelseType == HendelseType.SENDT && namespace == "q2") {
                        sakId = penService.hentSakId(identifisertPerson.aktoerId, sedHendelse.bucType)
                    }
                }
                var forsokFerdigstill = false
                sakId?.let { forsokFerdigstill = true }

                val journalPostResponse = journalpostService.opprettJournalpost(
                        rinaSakId = sedHendelse.rinaSakId,
                        fnr = identifisertPerson.fnr,
                        personNavn = identifisertPerson.personNavn,
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
                        avsenderNavn = sedHendelse.avsenderNavn
                )

                if(!journalPostResponse!!.journalpostferdigstilt) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, journalPostResponse.journalpostId, tildeltEnhet, identifisertPerson.aktoerId, "JOURNALFORING", sedHendelse, hendelseType)

                    if (uSupporterteVedlegg.isNotEmpty()) {
                        publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, null, tildeltEnhet, identifisertPerson.aktoerId, "BEHANDLE_SED", sedHendelse, hendelseType, usupporterteFilnavn(uSupporterteVedlegg))

                    }
                } else {
                    if(sakId != null && namespace == "q2") {
                        journalpostService.oppdaterDistribusjonsinfo(journalPostResponse.journalpostId)
                    }
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
        if(sedHendelse.sedType == SedType.P2100 || sedHendelse.sedType == SedType.P15000) {
            return try{
                fagmodulService.hentYtelseKravType(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            } catch (ex: Exception) {
                null
            }
        }
        return null
    }

    private fun hentTildeltEnhet(
            sedType: SedType,
            bucType: BucType,
            ytelseType: String?,
            person: IdentifisertPerson
    ): OppgaveRoutingModel.Enhet {
        return if(sedType == SedType.P15000){
            oppgaveRoutingService.route(person, bucType, ytelseType)
        } else {
            oppgaveRoutingService.route(person, bucType)
        }
    }
}