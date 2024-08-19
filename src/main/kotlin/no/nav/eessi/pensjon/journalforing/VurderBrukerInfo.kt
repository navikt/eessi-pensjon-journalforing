package no.nav.eessi.pensjon.journalforing

import com.google.cloud.storage.BlobId
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class VurderBrukerInfo (
    private val gcpStorageService: GcpStorageService,
    private val journalpostService: JournalpostService,
    private val oppgaveHandler: OppgaveHandler,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()){

    private val logger = LoggerFactory.getLogger(VurderBrukerInfo::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    fun journalPostUtenBruker(
        journalpostRequest: OpprettJournalpostRequest?,
        sedHendelse: SedHendelse,
        sedHendelseType: HendelseType
    ) {
        val lagretJournalpost = JournalpostMedSedInfo(journalpostRequest!!, sedHendelse, sedHendelseType)
        logger.debug("""Journalposten mangler bruker og vil bli lagret for fremtidig vurdering
            | ${lagretJournalpost.toJson()}
        """.trimMargin())

        gcpStorageService.lagreJournalPostRequest(
            lagretJournalpost.toJson(),
            sedHendelse.rinaSakId,
            sedHendelse.sedId
        )
        countForOppdatering("Lagerer journalpostid for journalpost uten bruker")
    }

    fun journalpostMedBruker(
        jprMedBruker: OpprettJournalpostRequest,
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson?,
        bruker: Bruker,
        saksbehandlerIdent: String?
    ) {
        try {
            gcpStorageService.arkiverteSakerForRinaId(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)?.forEach { rinaId ->
                logger.info("Henter tidligere journalføring for å sette bruker for sed: $rinaId")

                gcpStorageService.hentOpprettJournalpostRequest(rinaId)?.let { (journalpost, blob) ->
                    val lagretJournalPost = mapJsonToAny<JournalpostMedSedInfo>(journalpost)

                    val jprUtenBrukerOppdatert = lagretJournalPost.copy(
                        journalpostRequest = updateRequest(lagretJournalPost.journalpostRequest, jprMedBruker)
                    )

                    logger.info("Henter lagret sed: ${jprUtenBrukerOppdatert.journalpostRequest.tittel} fra gcp, rinaid: $rinaId")

                    val opprettetJournalpost = journalpostService.sendJournalPost(
                        jprUtenBrukerOppdatert,
                        saksbehandlerIdent
                    )

                    opprettetJournalpost?.let {
                        opprettOppgave(jprUtenBrukerOppdatert, it, identifisertPerson)

                        logger.info("Opprettet journalpost: ${it.journalpostId} med status: ${it.journalstatus}")

                        deleteJournalpostDetails(blob).also {
                            logger.info("Ferdig sletting av lagret journalpost: ${opprettetJournalpost.journalpostId}")
                        }
                    } ?: logger.error("Kunne ikke opprette journalpost for rinaId: $rinaId")
                } ?: logger.error("Ingen lagret request funnet for rinaId: $rinaId")
            }
        } catch (e: Exception) {
            logger.error("Det har skjedd feil med oppdatering av lagret journalpost uten bruker", e)
        }
    }

    private fun updateRequest(originalRequest: OpprettJournalpostRequest, jprMedBruker: OpprettJournalpostRequest): OpprettJournalpostRequest {
        return originalRequest.copy(
            tema = jprMedBruker.tema,
            bruker = jprMedBruker.bruker,
            journalfoerendeEnhet = jprMedBruker.journalfoerendeEnhet
        )
    }

    fun opprettOppgave(
        jprUtenBrukerOppdatert: JournalpostMedSedInfo,
        opprettetJournalpost: OpprettJournalPostResponse?,
        identifisertPerson: IdentifisertPerson?
    ) {
        val melding = OppgaveMelding(
            jprUtenBrukerOppdatert.sedHendelse.sedType,
            opprettetJournalpost?.journalpostId,
            jprUtenBrukerOppdatert.journalpostRequest.journalfoerendeEnhet!!,
            identifisertPerson?.aktoerId,
            jprUtenBrukerOppdatert.sedHendelse.rinaSakId,
            if (jprUtenBrukerOppdatert.journalpostRequest.journalpostType == JournalpostType.INNGAAENDE) HendelseType.MOTTATT else HendelseType.SENDT,
            null,
            if (jprUtenBrukerOppdatert.journalpostRequest.journalpostType == JournalpostType.INNGAAENDE) OppgaveType.JOURNALFORING else OppgaveType.JOURNALFORING_UT,
            tema = jprUtenBrukerOppdatert.journalpostRequest.tema
        )
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
    }

    private fun deleteJournalpostDetails(blobId: BlobId) {
        gcpStorageService.slettJournalpostDetaljer(blobId)
        countForOppdatering("Sletter fra journalpostid etter oppdatering av oppgave og journalpost")
    }

    private fun countForOppdatering(melding: String) {
        try {
            Metrics.counter("eessi_pensjon_journalforing_vurder_bruker", "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding: $melding", e)
        }
    }
}