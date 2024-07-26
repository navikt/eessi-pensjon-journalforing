package no.nav.eessi.pensjon.journalforing.skedulering

import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.LagretJournalpostMedSedInfo
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OpprettJournalpostUkjentBruker(
    private val gcpStorageService: GcpStorageService,
    private val journalpostService: JournalpostService,
    private val oppgaveHandler: OppgaveHandler,
) {
    companion object {
        const val EVERY_TWO_MIN = "0 0/2 * * * ?"
    }
    private val logger = LoggerFactory.getLogger(OpprettJournalpostUkjentBruker::class.java)

    @Scheduled(cron = EVERY_TWO_MIN)
    operator fun invoke() {
        val jp = gcpStorageService.hentGamleRinaSakerMedJPDetaljer(1)

        jp?.forEach { journalpostDetaljer ->
            mapJsonToAny<LagretJournalpostMedSedInfo>(journalpostDetaljer)
            .also {
                val response = journalpostService.sendJournalPost(it, "eessipensjon")

                logger.info("""Lagret JP hentet fra GCP: 
                | sedHendelse: ${it.sedHendelse}
                | enhet: ${it.journalpostRequest.journalfoerendeEnhet}
                | tema: ${it.journalpostRequest.tema}""".trimMargin()
                )

                if(response?.journalpostId != null) {
                    val melding = OppgaveMelding(
                        sedType = it.sedHendelse.sedType,
                        journalpostId = response.journalpostId,
                        tildeltEnhetsnr = it.journalpostRequest.journalfoerendeEnhet!!,
                        aktoerId = null,
                        rinaSakId = it.sedHendelse.rinaSakId,
                        hendelseType = it.sedHendelseType,
                        filnavn = null,
                        oppgaveType = OppgaveType.JOURNALFORING,
                    ).also { oppgaveMelding ->  logger.info("Opprettet journalforingsoppgave for sak med rinaId: ${oppgaveMelding.rinaSakId}") }
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                } else {
                    logger.error("Journalpost ikke opprettet")
                }
        } }
    }
}
