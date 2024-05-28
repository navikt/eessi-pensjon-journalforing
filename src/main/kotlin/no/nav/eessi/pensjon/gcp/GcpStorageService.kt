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
import java.time.temporal.ChronoUnit

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_NAME_GJENNY}") var gjennyBucket: String,
    @param:Value("\${GCP_BUCKET_NAME_JOURNAL}") var journalBucket: String,
    private val gcpStorage: Storage) {
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

    fun gjennyFinnes(storageKey: String) : Boolean{
        return eksisterer(storageKey, gjennyBucket)
    }
    fun journalFinnes(storageKey: String) : Boolean{
        return eksisterer(storageKey, journalBucket)
    }

    private fun eksisterer(storageKey: String, bucketName: String): Boolean{

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
            if(jsonHendelse.exists()){
                logger.info("Henter melding med rinanr $storageKey, for bucket $bucketName")
                return jsonHendelse.getContent().decodeToString()
            }
        } catch ( ex: Exception) {
            logger.warn("En feil oppstod under henting av objekt: $storageKey i bucket")
        }
        return null
    }

    fun lagreJournalpostDetaljer(journalpostId: String?, rinaSakId: String, rinaDokumentId: String, sedType: SedType?, eksternReferanseId: String) {
        val journalpostDetaljer = JournalpostDetaljer(journalpostId, rinaSakId, rinaDokumentId, sedType, eksternReferanseId)
        val blob = gcpStorage.create(
            BlobInfo.newBuilder(journalBucket, rinaSakId).setContentType("application/json").build(),
            journalpostDetaljer.toJson().toByteArray()
        )
        logger.info("""Journalpostdetaljer lagret i bucket: 
            | $journalBucket, med key: ${blob.name}
            | innhold""".trimMargin() + journalpostDetaljer.toJson())
    }

    fun lagreJournalPostRequest(request: String?, rinaId: String?, sedId: String?) {
        try {
            if(rinaId == null || sedId == null) {
                logger.error("Mangler informasjon fra: $rinaId, sedId: $sedId")
                return
            }
            val path = "${rinaId}/${sedId}"
            logger.info("Storing sedhendelse to S3: $path")
            val blob = gcpStorage.create(
                BlobInfo.newBuilder(journalBucket, path).setContentType("application/json").build(),
                request?.toByteArray()
            )
            logger.debug("""Journalpostdetaljer lagret i bucket: 
                | $journalBucket, med key: ${blob.name}
                | innhold""".trimMargin() + request)
        } catch (_: Exception) {
            logger.warn("Feil under lagring av journalpost request")
        }
    }

    private fun hentJournalPost(rinaId: String, sedId: String): String? {
        try {
            val path = "${rinaId}/${sedId}"
            val request = gcpStorage.get(BlobId.of(journalBucket, path))
            if(request.exists()){
                logger.info("Henter melding med rinanr $rinaId, for bucket $journalBucket")
                return request.getContent().decodeToString()
            }
        } catch ( ex: Exception) {
            logger.warn("En feil oppstod under henting av objekt: $rinaId i bucket")
        }
        return null
    }

    fun arkiverteSakerForRinaId(rinaId: String) {
        try {
            val blobs = gcpStorage.list(journalBucket)
            for (blob in blobs.iterateAll()) {
                logger.debug(blob.name)
                if(blob.name.contains(rinaId)){
                    logger.info("Vi har treff på en tidligere sak som mangler bruker")
                }
            }
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under henting av objekt: $rinaId i bucket")
        }
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
