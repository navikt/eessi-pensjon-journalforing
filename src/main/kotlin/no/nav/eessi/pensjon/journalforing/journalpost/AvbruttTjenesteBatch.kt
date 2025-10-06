package no.nav.eessi.pensjon.journalforing.journalpost

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service


@Service
@EnableScheduling
class AvbruttTjenesteBatch (
    private val journalpostKlient: JournalpostKlient
) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(AvbruttTjenesteBatch::class.java) }

    @Scheduled(cron = "0 57 9 * * *")
    fun settJournalposterTilAvbrutt() {
        logger.info("Starter batch for å sette journalposter til avbrutt")
        val names = listOf("673823640")
        names.forEach { name ->
            journalpostKlient.oppdaterJournalpostMedAvbrutt(name)
        }
    }
}