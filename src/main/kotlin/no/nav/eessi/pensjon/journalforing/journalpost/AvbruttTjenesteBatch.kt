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

    @Scheduled(cron = "0 12 11 * * *")
    fun settJournalposterTilAvbrutt() {
        logger.info("Starter batch for å sette journalposter til avbrutt")
        val names = listOf(
            "673000018",
            "673823581"
        )
        try {
            names.forEach { name ->
                journalpostKlient.oppdaterJournalpostMedAvbrutt(name)
                Thread.sleep(3000)
            }
        } catch (e: Exception) {
            logger.error("Feil ved oppdatering av journalpost til avbrutt", e)
        }
    }
}