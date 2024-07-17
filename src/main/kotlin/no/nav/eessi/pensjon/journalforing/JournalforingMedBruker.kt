package no.nav.eessi.pensjon.journalforing

import com.google.cloud.storage.BlobId
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.Journalstatus.*
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppdaterOppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class JournalforingMedBruker (
    private val safClient: SafClient,
    private val gcpStorageService: GcpStorageService,
    private val journalpostService: JournalpostService,
    private val oppgaveHandler: OppgaveHandler,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()){

    private val logger = LoggerFactory.getLogger(JournalforingMedBruker::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    fun harJournalpostBruker(
        journalpostResponse: OpprettJournalPostResponse?,
        journalpostRequest: OpprettJournalpostRequest,
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson?
    ) {
        journalpostRequest.bruker?.let {
            journalpostMedBruker(journalpostRequest, sedHendelse, identifisertPerson, it)
        } ?: run {
            journalPostUtenBruker(journalpostResponse, sedHendelse)
        }
    }

    private fun journalPostUtenBruker(
        journalpostResponse: OpprettJournalPostResponse?,
        sedHendelse: SedHendelse
    ) {
        logger.info("Journalposten mangler bruker og vil bli lagret for fremtidig vurdering")
        gcpStorageService.lagreJournalPostRequest(
            journalpostResponse?.journalpostId,
            sedHendelse.rinaSakId,
            sedHendelse.sedId
        )
        countForOppdatering("Lagerer journalpostid for journalpost uten bruker")
    }

    /***
     *  se om vi har lagret sed fra samme buc. Hvis ja; se om vi har bruker vi kan benytte i lagret sedhendelse
     */
    fun journalpostMedBruker(
        journalpostRequest: OpprettJournalpostRequest,
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson?,
        bruker: Bruker
    ) {
        try {
            gcpStorageService.arkiverteSakerForRinaId(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                ?.forEach { rinaId ->
                    logger.info("Henter tidligere journalføring for å sette bruker for sed: $rinaId")

                    val journalpostInfo = gcpStorageService.hentOpprettJournalpostRequest(rinaId) ?: return
                    logger.info("Henter informasjon fra SAF for: ${journalpostInfo.first}, rinaid: $rinaId")

                    val innhentetJournalpost = safClient.hentJournalpost(journalpostInfo.first)
                    if (innhentetJournalpost == null) {
                        logger.warn("Journalpost not found for ID: ${journalpostInfo.first}")
                        return
                    }

                    logger.info("Hentet journalpost: ${innhentetJournalpost.journalpostId} med status: ${innhentetJournalpost.journalstatus}")

                    if (innhentetJournalpost.journalstatus in listOf(UNDER_ARBEID, MOTTATT, AVBRUTT, UKJENT_BRUKER, UKJENT, OPPLASTING_DOKUMENT)) {
                        oppdaterJournalpost(innhentetJournalpost, journalpostRequest, bruker).also { logger.info("Ferdig med oppdatering av journalpost" )}
                        ferdigstillJournalpost(innhentetJournalpost, sedHendelse, identifisertPerson, journalpostRequest).also { logger.info("Ferdig forsøke ferdigstilling av journalpost" )}
                        deleteJournalpostDetails(journalpostInfo.second).also { logger.info("Ferdig sletting av lagret journalpost: ${innhentetJournalpost.journalpostId}" )}
                    }
                }
        } catch (e: Exception) {
            logger.error("Det har skjedd feil med henting av arkivert saker")
        }
    }

    private fun ferdigstillJournalpost(
        innhentetJournalpost: JournalpostResponse,
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson?,
        journalpostRequest: OpprettJournalpostRequest
    ) {
        val result = journalpostService.ferdigstilljournalpost(
            innhentetJournalpost.journalpostId!!,
            innhentetJournalpost.journalforendeEnhet!!
        )

        when (result) {
            is JournalpostModel.Ferdigstilt -> {
                logger.info(result.description)
            }

            is JournalpostModel.IngenFerdigstilling -> {
                logger.warn(result.description)
                opprettOppgave(sedHendelse, innhentetJournalpost, identifisertPerson, journalpostRequest)
            }
        }
    }

    fun opprettOppgave(
        sedHendelse: SedHendelse,
        innhentetJournalpost: JournalpostResponse,
        identifisertPerson: IdentifisertPerson?,
        journalpostRequest: OpprettJournalpostRequest
    ) {
        val melding = OppgaveMelding(
            sedHendelse.sedType,
            innhentetJournalpost.journalpostId,
            Enhet.getEnhet(innhentetJournalpost.journalforendeEnhet!!)!!,
            identifisertPerson?.aktoerId,
            sedHendelse.rinaSakId,
            if (journalpostRequest.journalpostType == JournalpostType.INNGAAENDE) HendelseType.MOTTATT else HendelseType.SENDT,
            null,
            if (journalpostRequest.journalpostType == JournalpostType.INNGAAENDE) OppgaveType.JOURNALFORING else OppgaveType.JOURNALFORING_UT,
            tema = innhentetJournalpost.tema
        )
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
    }

    fun oppdaterOppgave(
        rinaId: String,
        innhentetJournalpost: JournalpostResponse,
        journalpostRequest: OpprettJournalpostRequest,
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson?
    ) {
        logger.info("Lager oppgavemelding og oppdaterer rinasak: $rinaId med status: ${innhentetJournalpost}")

        val oppgaveMelding = OppdaterOppgaveMelding(
            id = innhentetJournalpost.journalpostId!!,
            status = innhentetJournalpost.journalstatus!!.name,
            tildeltEnhetsnr = journalpostRequest.journalfoerendeEnhet!!,
            tema = journalpostRequest.tema.name,
            aktoerId = identifisertPerson?.aktoerId,
            rinaSakId = sedHendelse.rinaSakId
        )

        oppgaveHandler.oppdaterOppgaveMeldingPaaKafkaTopic(oppgaveMelding).also {
            secureLog.info("Oppdatert oppgave $it")
            countForOppdatering("Oppdaterer oppgave med bruker fra ny sed med bruker")
        }
    }

    fun oppdaterJournalpost(
        innhentetJournalpost: JournalpostResponse,
        journalpostRequest: OpprettJournalpostRequest,
        bruker: Bruker
    ) {
        logger.info("Oppdaterer journalpost med JPID: ${innhentetJournalpost.journalpostId}")

        journalpostService.oppdaterJournalpost(
            journalpostResponse = innhentetJournalpost,
            kjentBruker = bruker,
            tema = journalpostRequest.tema,
            enhet = journalpostRequest.journalfoerendeEnhet!!,
            behandlingsTema = journalpostRequest.behandlingstema ?: innhentetJournalpost.behandlingstema!!
        ).also {
            secureLog.info(
                """Henter opprettjournalpostRequest:
                        | ${it.toJson()}   
                        | ${bruker.toJson()}""".trimMargin()
            )
            countForOppdatering("Oppdaterer journalpost med bruker fra ny sed med bruker")
        }
    }

    private fun deleteJournalpostDetails(blobId: BlobId) {
        gcpStorageService.slettJournalpostDetaljer(blobId).also {
            countForOppdatering("Sletter fra journalpostid etter oppdatering av oppgave og journalpost")
        }
    }

    private fun countForOppdatering(melding: String) {
        try {
            Metrics.counter("eessi_pensjon_journalføring_oppgave_oppdatering", "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding: $melding", e)
        }
    }
}