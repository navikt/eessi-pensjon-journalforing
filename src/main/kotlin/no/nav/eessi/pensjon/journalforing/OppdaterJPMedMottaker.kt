package no.nav.eessi.pensjon.journalforing

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@Component
class OppdaterJPMedMottaker(
    private val safClient: SafClient,
    private val euxService: EuxService,
    private val journalpostKlient: JournalpostKlient
) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(OppdaterJPMedMottaker::class.java) }

    /**
     * Henter Journalpost en etter en fra liste over Journalposter vi skal endre mottaker på
     * Hente tilhørende bucId fra journalposten
     * Hente deltakere for bucId
     * Oppdatere JP med Mottaker ("id", "idType" : "UTL_ORG", "navn", "land" )
     */

    @PostConstruct
    fun onStartup() {
        oppdatereHeleSulamitten()
    }

//    @Scheduled(cron = "0 0 21 * * ?")
    fun oppdatereHeleSulamitten() {

        val journalpostIderFile = File(javaClass.classLoader.getResource("JournalpostIder")!!.file)
        val journalpostIderSomGikkBraFile = File("JournalpostIderSomGikkBra")

        if (!journalpostIderFile.exists()) {
            journalpostIderFile.createNewFile()
        }
        if (!journalpostIderSomGikkBraFile.exists()) {
            journalpostIderSomGikkBraFile.createNewFile()
        }

        val inputStream = journalpostIderFile.inputStream()
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.forEach { journalpostId ->
                println("journalpostId: $journalpostId")
                if (journalpostId in journalpostIderSomGikkBraFile.bufferedReader().readLines()) {
                    logger.info("Journalpost $journalpostId er allerede oppdatert")
                    return@forEach
                }

                val rinaIder = hentRinaIdForJournalpost(journalpostId)?.let { it ->
                    euxService.hentDeltakereForBuc(it).also { logger.info("deltakere på Bucen: $it") }
                    // journalpostKlient.oppdaterJournalpostMedMottaker(journalpostId, mottaker.toJson())
                }

                journalpostIderSomGikkBraFile.appendText("$journalpostId\n")
                logger.info("Oppdatert journalpost med mottaker: $rinaIder")
            }
        }
    }

    fun readResourceFile(fileName: String): List<String> {
        val inputStream = object {}.javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalArgumentException("File not found: $fileName")
        return BufferedReader(InputStreamReader(inputStream)).readLines()
    }

    //Henter Journalpost en etter en fra liste over Journalposter vi skal endre mottaker på
    fun hentRinaIdForJournalpost(journalpostId: String): String? {
        val journalpost = safClient.hentJournalpostForJp(journalpostId)
        if (journalpost != null) {
            return journalpost.tilleggsopplysninger.firstOrNull()?.get("eessi_pensjon_bucid")
        }
        return null
    }

}
