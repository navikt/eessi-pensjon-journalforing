package no.nav.eessi.pensjon.journalforing.skedulering

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.JournalpostMedSedInfo
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Sjekker hver dag om det er journalposter som har ventet mer enn 14 dager
 * Sender disse til sending:
 *  1. Sett avbrutt
 *  2a. Lager oppgave om det innkommet sed
 *  3b. Lager ikke oppgave om det er utgående sed *
 */
@Component
class OpprettJournalpostUkjentBruker(
    private val gcpStorageService: GcpStorageService,
    private val journalforingService: JournalforingService,
    private val journalpostService: JournalpostService
) {
    private val logger = LoggerFactory.getLogger(OpprettJournalpostUkjentBruker::class.java)

    @Scheduled(cron = "0 0 21 * * ?")
    fun dagligSjekkForLagredeJournalposter() {
        val jp = gcpStorageService.hentGamleRinaSakerMedJPDetaljer(14)

        if ((jp?.size ?: 0) > 10) {
            logger.error("${jp?.size} mangler bruker og er muligens mer enn forventet")
        }

        logger.info("Daglig sjekk viser ${jp?.size} saker fra GCP som mangler bruker og som nå journalføres")
        jp?.forEach { journalpostDetaljer ->
            mapJsonToAny<JournalpostMedSedInfo>(journalpostDetaljer.first)
            .also {
                val journalPostUtenBruker = it.copy(journalpostRequest = it.journalpostRequest.copy(sak = null))
                    .also { logger.info("Tar ikke med sak: ${it.journalpostRequest.sak} hvor det mangler bruker" ) }

                val journalpostRequest = journalPostUtenBruker.journalpostRequest

                val journalpostResponse = journalpostService.sendJournalPost(journalPostUtenBruker, "eessipensjon").also {
                    logger.info("""Lagret JP hentet fra GCP: 
                    | sedHendelse: ${journalPostUtenBruker.sedHendelse}
                    | enhet: ${journalpostRequest.journalfoerendeEnhet}
                    | tema: ${journalpostRequest.tema}""".trimMargin())
                }
                journalforingService.vurderSettAvbruttOgLagOppgave(
                    fnr = Fodselsnummer.fra(journalpostRequest.bruker?.id),
                    hendelseType = journalPostUtenBruker.sedHendelseType,
                    sedHendelse = journalPostUtenBruker.sedHendelse,
                    journalPostResponse = journalpostResponse,
                    tildeltJoarkEnhet = journalpostRequest.journalfoerendeEnhet!!,
                    aktoerId = journalpostRequest.bruker?.id,
                    tema = journalpostRequest.tema
                )

                gcpStorageService.slettJournalpostDetaljer(journalpostDetaljer.second).also { logger.info("") }
                journalforingService.metricsOppdatering("Sletter automatisk lagret journalpost som har gått over 14 dager")
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
            val lagretJournalpostMedSedInfo = mapJsonToAny<JournalpostMedSedInfo>(journalpostDetaljer.first)

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
