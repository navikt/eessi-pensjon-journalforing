package no.nav.eessi.pensjon.journalforing

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.*

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
        val journalpostIderSomGikkBraFile = JournalpostIderSomGikkBra.loadFromFile()

        logger.info("journalpostIderSomGikkBraFile: ${journalpostIderSomGikkBraFile.toJson()}")

        val journalpostIderContent = readFileUsingGetResource("/JournalpostIder")
        val journalpostIderList = journalpostIderContent.lines()

        journalpostIderList.forEach { journalpostId ->
            logger.info("journalpostId: $journalpostId")
            if (journalpostId in journalpostIderSomGikkBraFile) {
                logger.info("Journalpost $journalpostId er allerede oppdatert")
                return@forEach
            }

            hentRinaIdForJournalpost(journalpostId)?.let { it ->
                val mottaker = euxService.hentDeltakereForBuc(it).also { logger.info("deltakere på Bucen: ${it.toJsonSkipEmpty()}") }
                journalpostKlient.oppdaterJournalpostMedMottaker(
                    journalpostId,
                    """{
                            "avsenderMottaker" : {
                                "id" : "${mottaker.id}",
                                "idType" : "UTL_ORG",
                                "navn" : "${mottaker.name}",
                                "land" : "${mottaker.countryCode}"
                            }
                        }
                 """.trimIndent()
                )
                logger.info("Oppdatert journalpost med mottaker: ${mottaker.id}, navn: ${mottaker.name}, land: ${mottaker.countryCode}")
            }
            if (!journalpostIderSomGikkBraFile.contains(journalpostId)) {
                JournalpostIderSomGikkBra.appendToFile(journalpostId)
            }
        }
    }

    object JournalpostIderSomGikkBra {
        private const val FILE_NAME = "/tmp/journalpostIderSomGikkBra.txt"

        fun appendToFile(newId: String) {
            BufferedWriter(FileWriter(FILE_NAME, true)).use { writer ->
                writer.write(newId)
                writer.newLine()
            }
        }

        fun loadFromFile(): List<String> {
            val file = File(FILE_NAME)
            return if (file.exists()) {
                file.readLines()
            } else {
                emptyList()
            }
        }
    }

    fun readFileUsingGetResource(fileName: String) = this::class.java.getResource(fileName).readText(Charsets.UTF_8)

    fun readResourceFile(fileName: String): List<String> {
        val inputStream = object {}.javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalArgumentException("File not found: $fileName")
        return BufferedReader(InputStreamReader(inputStream)).readLines()
    }

    //Henter Journalpost en etter en fra liste over Journalposter vi skal endre mottaker på
    fun hentRinaIdForJournalpost(journalpostId: String): String? {
        val journalpost = safClient.hentJournalpostForJp(journalpostId)
        if (journalpost != null) {
            return journalpost.tilleggsopplysninger.firstOrNull()?.get("verdi")
        }
        return null
    }

}
