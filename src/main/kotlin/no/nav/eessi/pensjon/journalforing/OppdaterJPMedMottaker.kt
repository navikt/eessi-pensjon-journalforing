package no.nav.eessi.pensjon.journalforing

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.*

private const val FILE_NAME_OK = "/tmp/journalpostIderSomGikkBra.txt"
private const val FILE_NAME_ERROR = "/tmp/journalpostIderSomFeilet.txt"

@Component
class OppdaterJPMedMottaker(
    private val safClient: SafClient,
    private val euxService: EuxService,
    private val journalpostKlient: JournalpostKlient,
    @Value("\${journalfil.ok:/tmp/journalpostIderSomGikkBra.txt}") private val okFil: String,
    @Value("\${journalfil.feil:/tmp/journalpostIderSomFeilet.txt}") private val feilFil: String

) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(OppdaterJPMedMottaker::class.java) }
    private val okLager = JournalpostIdFilLager(okFil)
    private val feilLager = JournalpostIdFilLager(feilFil)
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
        logger.info("journalpostIderSomGikkBraFile: ${okLager.hentAlle()}")

        val journalpostIderContent = readFileUsingGetResource("/JournalpostIder")
        val journalpostIderList = journalpostIderContent.lines()

        journalpostIderList.forEach { journalpostId ->
            logger.info("journalpostId: $journalpostId")
            if (journalpostId in okFil) {
                logger.info("Journalpost $journalpostId er allerede oppdatert")
                return@forEach
            }

            hentRinaIdForJournalpost(journalpostId)?.let { it ->
                runCatching {
                    val mottaker = euxService.hentDeltakereForBuc(it)
                    logger.info("Oppdaterer journalpost med mottaker: ${mottaker.id}, navn: ${mottaker.name}, land: ${mottaker.countryCode}")
                    journalpostKlient.oppdaterJournalpostMedMottaker(
                        journalpostId,
                        """{
                                "avsenderMottaker" : {
                                    "id" : "${mottaker.id}",
                                    "idType" : "UTL_ORG",
                                    "navn" : "${mottaker.name}",
                                    "land" : "SE"
                                }
                            }
                     """.trimIndent()
                    )
                    okLager.leggTil(journalpostId).also { logger.info("Oppdaterer journalpost $it") }
                }.onFailure { e ->
                    logger.error("Feil under oppdatering av journalpost: ${journalpostId}, rinaid: $it, feil: ${e.message}")
                    feilLager.leggTil(journalpostId)
                }
            }
        }
    }

    class JournalpostIdFilLager(private val fileName: String) {
        fun leggTil(newId: String) {
            BufferedWriter(FileWriter(fileName, true)).use { writer ->
                writer.write(newId)
                writer.newLine()
            }
        }

        fun hentAlle(): List<String> {
            val file = File(fileName)
            return if (file.exists()) file.readLines() else emptyList()
        }
    }

    fun readFileUsingGetResource(fileName: String) = this::class.java.getResource(fileName).readText(Charsets.UTF_8)

    //Henter Journalpost en etter en fra liste over Journalposter vi skal endre mottaker på
    fun hentRinaIdForJournalpost(journalpostId: String): String? {
        val journalpost = safClient.hentJournalpostForJp(journalpostId)
        if (journalpost != null) {
            return journalpost.tilleggsopplysninger.firstOrNull()?.get("verdi")
        }
        return null
    }

}
