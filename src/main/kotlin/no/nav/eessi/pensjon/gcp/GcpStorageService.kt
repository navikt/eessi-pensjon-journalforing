package no.nav.eessi.pensjon.gcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_NAME_GJENNY}") var bucketnameGjenny: String,
    @param:Value("\${GCP_BUCKET_NAME_JOURNAL}") var bucketnameJournal: String,
    private val gcpStorage: Storage) {
    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        ensureBucketExists(bucketnameGjenny)
        ensureBucketExists(bucketnameJournal)
    }

    private fun ensureBucketExists(bucketName: String) {
        when (gcpStorage.get(bucketName) != null) {
            false -> throw IllegalStateException("Fant ikke bucket med navn $bucketName. Må provisjoneres")
            true -> logger.info("Bucket $bucketName funnet.")
        }
    }

    fun gjennyFinnes(storageKey: String) : Boolean{
        return eksisterer(storageKey, bucketnameGjenny)
    }
    fun journalFinnes(storageKey: String) : Boolean{
        return eksisterer(storageKey, bucketnameJournal)
    }

    private fun eksisterer(storageKey: String, bucketName: String): Boolean{

        val obj = gcpStorage.get(BlobId.of(bucketName, storageKey))

        kotlin.runCatching {
            obj.exists().also {logger.debug("sjekker om $storageKey finnes i bucket: $bucketName") }
        }.onFailure {
            logger.info("Blob $storageKey eksiterer ikke for $bucketName")
        }.onSuccess {
            logger.info("Blob $storageKey eksiterer for $bucketName")
            return true
        }
        return false
    }

    fun hentFraGjenny(storageKey: String): String? {
        return hent(storageKey, bucketnameGjenny)
    }
    fun hentFraJournal(storageKey: String): String? {
        return hent(storageKey, bucketnameJournal)
    }

    private fun hent(storageKey: String, bucketName: String): String? {
        val jsonHendelse: Blob
        try {
            jsonHendelse =  gcpStorage.get(BlobId.of(bucketName, storageKey))
            if(jsonHendelse.exists()){
                logger.info("Blob med key:$storageKey funnet")
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
            BlobInfo.newBuilder(bucketnameJournal, rinaSakId)
                .setContentType("application/json")
                .build(),
            journalpostDetaljer.toJson().toByteArray()
        )
        logger.info("Journalpostdetaljer lagret i bucket: $bucketnameJournal, med key: ${blob.name}")

    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalpostDetaljer(
    val journalpostId: String?,
    val rinaSakId: String,
    val rinaDokumentId: String,
    val sedType: SedType?,
    val eksternReferanseId: String
)
