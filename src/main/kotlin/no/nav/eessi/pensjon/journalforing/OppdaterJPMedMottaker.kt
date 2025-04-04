package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val FILE_NAME_OK = "/tmp/journalpostIderSomGikkBra.txt"
private const val FILE_NAME_ERROR = "/tmp/journalpostIderSomFeilet.txt"

@Component
class OppdaterJPMedMottaker(
    private val safClient: SafClient,
    private val euxService: EuxService,
    private val journalpostKlient: JournalpostKlient,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
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

    @Scheduled(cron = "0 40 09 * * ?")
    fun oppdatereHeleSulamitten() {
        logger.debug("journalpostIderSomGikkBraFile: ${journalpostIderSomGikkBraFile.hentAlle()}")

        val journalpostIderList = readFileUsingGetResource()

        val journalposterOK = journalpostIderSomGikkBraFile.hentAlle()
        val journalposterError = journalpostIderSomFeilet.hentAlle()
        journalpostIderList
            .filterNot { it in journalposterOK || it in journalposterError }
            .forEachIndexed { index, journalpostId ->
                if ((index + 1) % 1000 == 0) logger.info("Prosessert ${index + 1} journalposter")

                val rinaId = hentRinaIdForJournalpost(journalpostId) ?: return@forEachIndexed
                val rinaNrOgMottaker = mutableMapOf<String, AvsenderMottaker>()
                runCatching {

                    val mottaker = (rinaNrOgMottaker[rinaId]) ?: hentDeltakerOgMottaker(rinaId).also {
                        rinaNrOgMottaker.put(rinaId, it)
                    }
                    logger.info("Mottaker $mottaker")

                    journalpostKlient.oppdaterJournalpostMedMottaker(
                        journalpostId, JournalpostResponse(
                            avsenderMottaker = mottaker
                        ).toJsonSkipEmpty()
                    )
                    journalpostIderSomGikkBraFile.leggTil(journalpostId.plus(", $rinaId"))
                    logger.info("Journalpost: $journalpostId ferdig oppdatert: resultat: $rinaId, mottaker: ${mottaker}")
                }.onFailure {
                    logger.error("Feil under oppdatering av $journalpostId (rinaId: $rinaId)", it)
                    journalpostIderSomFeilet.leggTil(journalpostId.plus(", $rinaId"))
                }
            }
    }

    private fun hentDeltakerOgMottaker(rinaId: String): AvsenderMottaker {
        val mottaker = euxService.hentDeltakereForBuc(rinaId)
            ?.firstOrNull { it.organisation?.countryCode != "NO" }?.organisation
            ?: throw IllegalStateException("Fant ingen utenlandsk mottaker for rinaId: $rinaId")
        logger.info("Henter mottaker fra Eux")
        return AvsenderMottaker(
            id = mottaker.id,
            idType = IdType.UTL_ORG,
            navn = mottaker.name,
            land = konverterGBUKLand(mottaker.countryCode)
        )
    }

    fun konverterGBUKLand(mottakerAvsenderLand: String?): String? =
        when (mottakerAvsenderLand) {
            "UK" -> "GB"
            else -> mottakerAvsenderLand
        }


    class JournalpostIdFilLager(private val fileName: String) {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

        init {
            val file = File(fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
        }

        fun leggTil(newId: String) {
            val timestamp = LocalTime.now().format(timeFormatter)
            BufferedWriter(FileWriter(fileName, true)).use { writer ->
                writer.write("$newId -$timestamp")
                writer.newLine()
            }
        }

        fun hentAlle(): Set<String> {
            val file = File(fileName)
            return if (file.exists()) file.readLines()
                .mapNotNull { it.split(" ").firstOrNull() }
                .toSet() else emptySet()
        }
    }

    fun readFileUsingGetResource(): Sequence<String> {
        val fileName = if (profile == "prod") "JournalpostIderPROD.txt" else "JournalpostIderTest.txt"
        return this::class.java.getResourceAsStream("/$fileName")
            ?.bufferedReader()
            ?.lineSequence()
            ?.mapNotNull { it.split(" ").firstOrNull() }
            ?: emptySequence()
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
