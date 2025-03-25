package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
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

    @Scheduled(cron = "0 0 21 * * ?")
    fun OppdatereHeleSulamitten() {
        File("JournalpostIder").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val rinaIder = hentRinaIdForJournalpost(line)?.let {
                    val mottaker  = euxService.hentDeltakereForBuc(it)
                    journalpostKlient.oppdaterJournalpostMedMottaker(line, mottaker.toJson())
                }
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
