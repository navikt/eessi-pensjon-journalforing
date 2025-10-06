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

    @Scheduled(cron = "0 25 10 * * *")
    fun settJournalposterTilAvbrutt() {
        logger.info("Starter batch for å sette journalposter til avbrutt")
        val names = listOf(
            "673823639",
            "673823580",
            "675636958",
            "675878639",
            "677455545",
            "678218560",
            "673000052",
            "673823645",
            "673000054",
            "679937129",
            "679937076",
            "679937130",
            "679505238",
            "679505249",
            "681655379",
            "674374032",
            "553465180",
            "619695191",
            "645153145",
            "643414733",
            "679937118",
            "673823582",
            "673000053",
            "673000049"
        )
        names.forEach { name ->
            journalpostKlient.oppdaterJournalpostMedAvbrutt(name)
            Thread.sleep(3000)
        }
    }
}