package no.nav.eessi.pensjon.journalforing

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

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
        OppdatereHeleSulamitten()
    }

//    @Scheduled(cron = "0 0 21 * * ?")
    fun OppdatereHeleSulamitten() {
    File(javaClass.classLoader.getResource("JournalpostIder")!!.file).bufferedReader().useLines { lines ->
            lines.forEach { journalpostId ->
                if(journalpostId in File(javaClass.classLoader.getResource("JournalpostIderSomGikkBra")!!.file).bufferedReader().readLines()) {
                    logger.info("Journalpost $journalpostId er allerede oppdatert")
                    return@forEach
                }

                val rinaIder = hentRinaIdForJournalpost(journalpostId)?.let { it ->
                    euxService.hentDeltakereForBuc(it).also { logger.info("deltakere på Bucen: $it") }
//                    journalpostKlient.oppdaterJournalpostMedMottaker(journalpostId, mottaker.toJson())
                }

                val file = File("JournalpostIderSomGikkBra")
                file.appendText("$journalpostId\n")
                logger.info("Oppdatert journalpost med mottaker: $rinaIder")
            }
        }
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
