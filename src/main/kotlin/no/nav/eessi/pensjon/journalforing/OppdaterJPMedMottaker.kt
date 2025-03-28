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
        val tempDirectory = File(System.getProperty("java.io.tmpdir"))

        val journalpostIderSomGikkBraFile = File(tempDirectory, "JournalpostIderSomGikkBra")

        if (!journalpostIderSomGikkBraFile.exists()) {
            logger.info("JournalpostIderSomGikkBraFile eksisterer ikke, oppretter ny fil")
            journalpostIderSomGikkBraFile.createNewFile()
        }

        val journalpostIderContent = readFileUsingGetResource("/JournalpostIder")
        val journalpostIderList = journalpostIderContent.lines()
        journalpostIderList.forEach { journalpostId ->
            logger.info("journalpostId: $journalpostId")
            if (journalpostId in journalpostIderSomGikkBraFile.bufferedReader().readLines()) {
                logger.info("Journalpost $journalpostId er allerede oppdatert")
                return@forEach
            }

            val rinaIder = hentRinaIdForJournalpost(journalpostId)?.let { it ->
                val mottaker = euxService.hentDeltakereForBuc(it).also { logger.info("deltakere på Bucen: ${it.toJsonSkipEmpty()}") }
                 journalpostKlient.oppdaterJournalpostMedMottaker(journalpostId,
                      """{
                            "avsenderMottaker" : {
                                "id" : "${mottaker.id}",
                                "idType" : "UTL_ORG",
                                "navn" : "${mottaker.name}",
                                "land" : "${mottaker.countryCode}"
                                }
                         }
                 """.trimIndent())
            }

            journalpostIderSomGikkBraFile.appendText("$journalpostId\n")
            logger.info("Oppdatert journalpost med mottaker: $rinaIder")
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
