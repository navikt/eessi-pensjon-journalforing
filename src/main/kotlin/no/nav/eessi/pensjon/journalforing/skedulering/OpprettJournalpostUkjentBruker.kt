package no.nav.eessi.pensjon.journalforing.skedulering

import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.LagretJournalpostMedSedInfo
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OpprettJournalpostUkjentBruker(
    private val gcpStorageService: GcpStorageService
) {
    companion object {
        const val EVERY_TWO_MIN = "0 0/2 * * * ?"
    }
    private val logger = LoggerFactory.getLogger(OpprettJournalpostUkjentBruker::class.java)

    @Scheduled(cron = EVERY_TWO_MIN)
    operator fun invoke() {
        val jp = gcpStorageService.hentGamleRinaSakerMedJPDetlajer(2)
        logger.info("Executing cron...sakerfunnet: $jp")

        jp?.forEach { mapJsonToAny<LagretJournalpostMedSedInfo>(it).also {
            logger.info("LagretJournalpostMedSedInfo: ${it.toJson()}")
        } }
    }
}
