package no.nav.eessi.pensjon.gcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_NAME_GJENNY}") var gjennyBucket: String,
    @param:Value("\${GCP_BUCKET_NAME_JOURNAL}") var journalBucket: String,
    private val gcpStorage: Storage
) {
    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        ensureBucketExists(gjennyBucket)
        ensureBucketExists(journalBucket)
    }

    private fun ensureBucketExists(bucketName: String) {
        when (gcpStorage.get(bucketName) != null) {
            false -> throw IllegalStateException("Fant ikke bucket med navn $bucketName. Må provisjoneres")
            true -> logger.info("Bucket $bucketName funnet.")
        }
    }

    fun gjennyFinnes(storageKey: String): Boolean {
        return eksisterer(storageKey, gjennyBucket)
    }

    fun journalFinnes(storageKey: String): Boolean {
        return eksisterer(storageKey, journalBucket)
    }

    private fun eksisterer(storageKey: String, bucketName: String): Boolean {

        val obj = gcpStorage.get(BlobId.of(bucketName, storageKey))

        kotlin.runCatching {
            obj.exists()
        }.onFailure {
        }.onSuccess {
            logger.info("Henter melding for $storageKey fra $bucketName")
            return true
        }
        logger.info("Melding for $storageKey finnes ikke for $bucketName")
        return false
    }

    fun hentFraGjenny(storageKey: String): String? {
        return hent(storageKey, gjennyBucket)
    }

    fun hentFraJournal(storageKey: String): JournalpostDetaljer? {
        return hent(storageKey, journalBucket)?.let { mapJsonToAny<JournalpostDetaljer>(it) }
    }

    private fun hent(storageKey: String, bucketName: String): String? {
        try {
            val jsonHendelse = gcpStorage.get(BlobId.of(bucketName, storageKey))
            if (jsonHendelse.exists()) {
                logger.info("Henter melding med rinanr $storageKey, for bucket $bucketName")
                return jsonHendelse.getContent().decodeToString()
            }
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under henting av objekt: $storageKey i bucket")
        }
        return null
    }

    fun lagreJournalpostDetaljer(
        journalpostId: String?,
        rinaSakId: String,
        rinaDokumentId: String,
        sedType: SedType?,
        eksternReferanseId: String
    ) {
        val journalpostDetaljer =
            JournalpostDetaljer(journalpostId, rinaSakId, rinaDokumentId, sedType, eksternReferanseId)
        val blob = gcpStorage.create(
            BlobInfo.newBuilder(journalBucket, rinaSakId).setContentType("application/json").build(),
            journalpostDetaljer.toJson().toByteArray()
        )
        logger.info(
            """Journalpostdetaljer lagret i bucket: 
            | $journalBucket, med key: ${blob.name}
            | innhold""".trimMargin() + journalpostDetaljer.toJson()
        )
    }

    fun slettJournalpostDetaljer(blobId: BlobId) {
        try {
            logger.info("Sletter journalpostdetaljer for rinaSakId: $blobId")
            gcpStorage.delete(blobId).also { logger.info("Slett av journalpostdetaljer utført: $it") }
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under sletting av objekt: $blobId i bucket")
        }
    }


    fun lagreJournalPostRequest(lagretJournalPost: String, rinaId: String?, dokId: String?) {
        try {
            if (rinaId == null || dokId == null) {
                logger.error("Mangler informasjon fra: $rinaId, sedId: $dokId eller journalpost")
                return
            }
            val path = "${rinaId}/${dokId}"
            logger.info("Storing sedhendelse to S3: $path")
            val blob = gcpStorage.create(
                BlobInfo.newBuilder(journalBucket, path).setContentType("application/json").build(),
                lagretJournalPost.toByteArray()
            )
            logger.debug(
                """Journalpostdetaljer lagret i bucket: 
                | $journalBucket, med key: ${blob.name}
                | innhold: ${lagretJournalPost}""".trimMargin()
            )
        } catch (_: Exception) {
            logger.warn("Feil under lagring av journalpost request")
        }
    }

    /**
     * Henter journalpost fra bucket med blobPath Pair<JournalPostId, BlobPath>
     */
    fun hentOpprettJournalpostRequest(rinaIdOgSedId: String): Pair<String, BlobId>? {
        try {
            val blobId = BlobId.of(journalBucket, rinaIdOgSedId)
            val journalpostIdFraUkjentBruker = gcpStorage.get(blobId)
            if (journalpostIdFraUkjentBruker.exists()) {
                logger.info("Henter melding med rinanr $rinaIdOgSedId, for bucket $journalBucket")
                val request = journalpostIdFraUkjentBruker.getContent().decodeToString()
                return Pair(request, blobId)
                    .also {
                        logger.debug(
                            """Journalpost fra ukjent bruker:
                        | blobid: $blobId
                        | rinaIdOgSedId: $rinaIdOgSedId
                        | $it""".trimMargin()
                        )
                    }
            } else {
                logger.error("Finner ikke lagret journalpostId for $rinaIdOgSedId, for bucket $journalBucket")
            }
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under henting av journalpostRequest objekt ved : $rinaIdOgSedId i bucket")
        }
        return null
    }

    fun arkiverteSakerForRinaId(rinaId: String, rinaDokumentId: String): List<String>? {
        try {
            logger.info("Henter arkiverte saker for RinaId: $rinaId, dokumentid: $rinaDokumentId")
            val blobs = gcpStorage.list(journalBucket)

            for (blob in blobs.iterateAll()) {
                logger.debug("Undersøker blob_name: ${blob.name} mot $rinaId")
                if (blob.name.contains(rinaId)) {
                    logger.info(
                        """Vi har treff på en tidligere buc: $rinaId som mangler bruker:
                        | dokument: $rinaDokumentId
                        | lagret jp: ${blob.name}
                    """.trimMargin()
                    )
                    return blobs.values.filter {
                        logger.info(it.name)
                        it.name.contains(rinaId)
                    }.map { it.name }.also { logger.info("Arkiverte saker: $it") }
                }
            }
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under henting av arkiverte rinasaker objekt: $rinaId i bucket", ex)
        }
        return null
    }

    fun hentGamleRinaSakerMedJPDetlajer(min: Long): List<String>? {
        try {
            logger.info("Henter rinasaker som er eldre enn $min dager gamle")
            val blobs = gcpStorage.list(journalBucket)

            val jpListe = mutableListOf<String>()
            for (blob in blobs.iterateAll()) {
                if (blob.createTimeOffsetDateTime.isBefore(OffsetDateTime.now().minusMinutes(min))) {
                    logger.info(
                        """fant følgende SED med rinaIder som er eldre enn $min dager:
                        | Eldre saker: ${blob.name}
                    """.trimMargin()
                    )
                    val jpId = gcpStorage.get(blob.blobId)
                    jpListe.add(jpId.getContent().decodeToString())
                }
            }
            return jpListe
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under henting av gamle rinasaker fra bucket", ex)
        }
        return null
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalpostDetaljer(
    val journalpostId: String?,
    val rinaSakId: String,
    val rinaDokumentId: String,
    val sedType: SedType?,
    val eksternReferanseId: String,
    val opprettet: LocalDateTime? = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
)
