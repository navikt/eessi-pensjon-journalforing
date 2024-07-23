package no.nav.eessi.pensjon.journalforing.skedulering

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OpprettJournalpostUkjentBruker {
    companion object {
        const val EVERY_TWO_MIN = "0 0/2 * * * ?"
    }
    private val logger = LoggerFactory.getLogger(OpprettJournalpostUkjentBruker::class.java)

    @Scheduled(cron = EVERY_TWO_MIN)
    operator fun invoke() {
        logger.info("Executing cron...")

    }
}
