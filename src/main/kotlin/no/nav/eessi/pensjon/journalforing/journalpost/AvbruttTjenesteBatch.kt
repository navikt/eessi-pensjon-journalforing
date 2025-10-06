package no.nav.eessi.pensjon.journalforing.journalpost

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service


@Service
@EnableScheduling
class AvbruttTjenesteBatch (
    private val journalpostKlient: JournalpostKlient
) {
    @Scheduled(cron = "0 45 9 * * *")
    fun settJournalposterTilAvbrutt() {
        val names = listOf("673823640")
        names.forEach { name ->
            journalpostKlient.oppdaterJournalpostMedAvbrutt(name)
        }
    }
}