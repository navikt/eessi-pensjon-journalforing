package no.nav.eessi.pensjon.journalforing.skedulering

import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
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
    private val journalforingService: JournalforingService
) {
    companion object {
        const val EVERY_DAY = "0 0 18 * * ?"
    }
    private val logger = LoggerFactory.getLogger(OpprettJournalpostUkjentBruker::class.java)

    @Scheduled(cron = EVERY_DAY)
    operator fun invoke() {
        val jp = gcpStorageService.hentGamleRinaSakerMedJPDetaljer(14)

        jp?.forEach { journalpostDetaljer ->
            mapJsonToAny<LagretJournalpostMedSedInfo>(journalpostDetaljer.first)
            .also {
                journalforingService.lagJournalpostOgOppgave(it, "eessipensjon", journalpostDetaljer.second)
        } }
    }
}
