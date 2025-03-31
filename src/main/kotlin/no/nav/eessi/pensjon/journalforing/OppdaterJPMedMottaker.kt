package no.nav.eessi.pensjon.journalforing

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.*
import java.time.LocalDateTime

private const val FILE_NAME_OK = "/tmp/journalpostIderSomGikkBra.txt"
private const val FILE_NAME_ERROR = "/tmp/journalpostIderSomFeilet.txt"

@Component
class OppdaterJPMedMottaker(
    private val safClient: SafClient,
    private val euxService: EuxService,
    private val journalpostKlient: JournalpostKlient

) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(OppdaterJPMedMottaker::class.java) }
    private val journalpostIderSomGikkBraFile = JournalpostIdFilLager(FILE_NAME_OK)
    private val journalpostIderSomFeilet = JournalpostIdFilLager(FILE_NAME_ERROR)
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
        logger.debug("journalpostIderSomGikkBraFile: ${journalpostIderSomGikkBraFile.hentAlle()}")

        val journalpostIderList = readFileUsingGetResource("/JournalpostIder")

        val ferdigeJournalposter = journalpostIderSomGikkBraFile.hentAlle().toSet()

        var count = 0
        journalpostIderList.forEach { journalpostId ->
            if (++count % 1000 == 0) logger.info("Prosessert $count journalposter")

            if (journalpostId in ferdigeJournalposter) {
                return@forEach
            }
            hentRinaIdForJournalpost(journalpostId)?.let { it ->
                runCatching {
                    val mottaker = euxService.hentDeltakereForBuc(it)
                    logger.debug("Oppdaterer journalpost med mottaker: ${mottaker.id}, navn: ${mottaker.name}, land: ${mottaker.countryCode}")

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
                    journalpostIderSomGikkBraFile.leggTil(journalpostId).also { logger.debug("Oppdaterer journalpost $it") }
                }.onFailure { e ->
                    logger.error("Feil under oppdatering av journalpost: ${journalpostId}, rinaid: $it, feil: ${e.message}")
                    journalpostIderSomFeilet.leggTil(journalpostId)
                }
            }
        }
    }

    class JournalpostIdFilLager(private val fileName: String) {
        fun leggTil(newId: String) {
            BufferedWriter(FileWriter(fileName, true)).use { writer ->
                writer.write(newId+"-"+LocalDateTime.now())
                writer.newLine()
            }
        }

        fun hentAlle(): List<String> {
            val file = File(fileName)
            return if (file.exists()) file.readLines() else emptyList()
        }
    }

    fun readFileUsingGetResource(fileName: String): Sequence<String> =
        this::class.java.getResourceAsStream(fileName)?.bufferedReader()?.lineSequence()?: emptySequence()

    //Henter Journalpost en etter en fra liste over Journalposter vi skal endre mottaker på
    fun hentRinaIdForJournalpost(journalpostId: String): String? {
        val journalpost = safClient.hentJournalpostForJp(journalpostId)
        if (journalpost != null) {
            return journalpost.tilleggsopplysninger.firstOrNull()?.get("verdi")
        }
        return null
    }

}
