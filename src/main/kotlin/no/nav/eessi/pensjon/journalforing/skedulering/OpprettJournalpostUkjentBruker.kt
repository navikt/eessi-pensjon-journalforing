package no.nav.eessi.pensjon.journalforing.skedulering

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.LagretJournalpostMedSedInfo
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OpprettJournalpostUkjentBruker(
    private val gcpStorageService: GcpStorageService,
    private val journalforingService: JournalforingService
) {
    private val logger = LoggerFactory.getLogger(OpprettJournalpostUkjentBruker::class.java)

    @Scheduled(cron = "0 0 18 * * ?")
    fun dagligSjekkForLagredeJournalposter() {
        val jp = gcpStorageService.hentGamleRinaSakerMedJPDetaljer(14)

        jp?.forEach { journalpostDetaljer ->
            mapJsonToAny<LagretJournalpostMedSedInfo>(journalpostDetaljer.first)
            .also {
                journalforingService.lagJournalpostOgOppgave(it, "eessipensjon", journalpostDetaljer.second)
        } }
    }

    @Scheduled(cron = "0 0 23 * * ?")
    fun henterRelevantInfoOmLagredeJournalposter() {
        val jp = gcpStorageService.hentGamleRinaSakerMedJPDetaljer(0)

        val sedTypeCounts = mutableMapOf<SedType, Int>().apply {
            SedType.entries.forEach { this[it] = 0 }
        }
        val bucTypeCounts = mutableMapOf<BucType, Int>().apply {
            BucType.entries.forEach { this[it] = 0 }
        }

        jp?.forEach { journalpostDetaljer ->
            val lagretJournalpostMedSedInfo = mapJsonToAny<LagretJournalpostMedSedInfo>(journalpostDetaljer.first)

            lagretJournalpostMedSedInfo.sedHendelse.sedType?.let {
                sedTypeCounts[it] = sedTypeCounts[it]!! + 1
            }
            lagretJournalpostMedSedInfo.sedHendelse.bucType?.let {
                bucTypeCounts[it] = bucTypeCounts[it]!! + 1
            }
        }

        val filteredSedTypeCounts = sedTypeCounts.filter { it.value > 1 }
        val filteredBucTypeCounts = bucTypeCounts.filter { it.value > 1 }

        val bucOgSedGCP = mapOf(
            "sedTypeCounts" to filteredSedTypeCounts,
            "bucTypeCounts" to filteredBucTypeCounts
        )

        if (bucOgSedGCP["sedTypeCounts"]!!.isNotEmpty() || bucOgSedGCP["bucTypeCounts"]!!.isNotEmpty()) {
            logger.info("""*************** LAGRET SED OG BUC PÅ GCP *****************
            | ${bucOgSedGCP.toJson()} """)
        }
    }
}
