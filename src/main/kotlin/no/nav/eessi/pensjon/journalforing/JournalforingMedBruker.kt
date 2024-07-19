package no.nav.eessi.pensjon.journalforing

import com.google.cloud.storage.BlobId
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppdaterOppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
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

//    fun harJournalpostBruker(
//        journalpostResponse: OpprettJournalPostResponse?,
//        journalpostRequest: OpprettJournalpostRequest,
//        sedHendelse: SedHendelse,
//        identifisertPerson: IdentifisertPerson?
//    ) {
//        journalpostRequest.bruker?.let {
//            journalpostMedBruker(journalpostRequest, sedHendelse, identifisertPerson, it)
//        } ?: run {
//            journalPostUtenBruker(journalpostResponse, sedHendelse)
//        }
//    }

    fun journalPostUtenBruker(
        journalpostRequest: OpprettJournalpostRequest?,
        sedHendelse: SedHendelse,
        sedHendelseType: HendelseType
    ) {
        logger.info("Journalposten mangler bruker og vil bli lagret for fremtidig vurdering")
        val lagretJournalpost = LagretJournalpostMedSedInfo(journalpostRequest!!, sedHendelse, sedHendelseType)

        gcpStorageService.lagreJournalPostRequest(
            lagretJournalpost.toJson(),
            sedHendelse.rinaSakId,
            sedHendelse.sedId
        )
        countForOppdatering("Lagerer journalpostid for journalpost uten bruker")
    }

    /***
     *  se om vi har lagret sed fra samme buc. Hvis ja; se om vi har bruker vi kan benytte i lagret sedhendelse
     */
    fun journalpostMedBruker(
        jprMedBruker: OpprettJournalpostRequest,
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson?,
        bruker: Bruker,
        saksbehandlerIdent: String?
    ) {
        try {
            gcpStorageService.arkiverteSakerForRinaId(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                ?.forEach { rinaId ->
                    logger.info("Henter tidligere journalføring for å sette bruker for sed: $rinaId")
                    val lagretRequest = gcpStorageService.hentOpprettJournalpostRequest(rinaId)
                    if (lagretRequest != null) {
                        val (journalpost, blob) = lagretRequest
                        val lagretJournalPost = mapJsonToAny<LagretJournalpostMedSedInfo>(journalpost)

                        val jprUtenBrukerOppdatert = lagretJournalPost.copy(
                            journalpostRequest = lagretJournalPost.journalpostRequest.copy(
                                tema = jprMedBruker.tema,
                                bruker = jprMedBruker.bruker,
                                journalfoerendeEnhet = jprMedBruker.journalfoerendeEnhet
                            )
                        )
                        logger.info("Henter lagret sed: ${jprUtenBrukerOppdatert.journalpostRequest.tittel} fra gcp, rinaid: $rinaId")

                        val opprettetJournalpost = journalpostService.sendJournalPost(
                            jprUtenBrukerOppdatert.journalpostRequest,
                            jprUtenBrukerOppdatert.sedHendelse,
                            jprUtenBrukerOppdatert.sedHendelseType,
                            saksbehandlerIdent
                        )

                        opprettOppgave(jprUtenBrukerOppdatert, opprettetJournalpost, identifisertPerson)

                        logger.info("Opprettet journalpost: ${opprettetJournalpost?.journalpostId} med status: ${opprettetJournalpost?.journalstatus}")

                        deleteJournalpostDetails(blob).also {
                            logger.info("Ferdig sletting av lagret journalpost: ${opprettetJournalpost?.journalpostId}")
                        }
                    }
                }
        } catch (e: Exception) {
            logger.error("Det har skjedd feil med oppdatering av lagret journalpost uten bruker", e)
        }
    }

    //    private fun opprettOppgave(
//        innhentetJournalpost: LagretJournalpostMedSedInfo,
//        opprettetJournalpost: OpprettJournalPostResponse?,
//        identifisertPerson: IdentifisertPerson?,
//        journalpostRequest: OpprettJournalpostRequest
//    ) {
//
//
////        val result = journalpostService.ferdigstilljournalpost(
////            innhentetJournalpost.journalpostId!!,
////            innhentetJournalpost.journalforendeEnhet!!
////        )
//
////        when (result) {
////            is JournalpostModel.Ferdigstilt -> {
////                logger.info(result.description)
////            }
////
////            is JournalpostModel.IngenFerdigstilling -> {
////                logger.warn(result.description)
//                opprettOppgave(innhentetJournalpost.sedHendelse, opprettetJournalpost?.journalpostId, identifisertPerson, journalpostRequest)
////            }
////        }
//    }

    fun opprettOppgave(
        jprUtenBrukerOppdatert: LagretJournalpostMedSedInfo,
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