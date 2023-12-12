package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_NAME}") var bucketname: String,
    private val gcpStorage: Storage) {
    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        ensureBucketExists()
    }

    private fun ensureBucketExists() {
        when (gcpStorage.get(bucketname) != null) {
            false -> throw IllegalStateException("Fant ikke bucket med navn $bucketname. Må provisjoneres")
            true -> logger.info("Bucket $bucketname funnet.")
        }
    }

    fun eksisterer(storageKey: String): Boolean{

        val obj = gcpStorage.get(BlobId.of(bucketname, storageKey))

        kotlin.runCatching {
            obj.exists().also {logger.debug("sjekker om $storageKey finnes i bucket: $bucketname") }
        }.onFailure {
        }.onSuccess {
            return true
        }
        return false
    }

    fun hent(storageKey: String): String? {
        val jsonHendelse: Blob
        try {
            jsonHendelse =  gcpStorage.get(BlobId.of(bucketname, storageKey))
            if(jsonHendelse.exists()){
                logger.info("Blob med key:$storageKey funnet")
                return jsonHendelse.getContent().decodeToString()
            }
        } catch ( ex: Exception) {
            logger.warn("En feil oppstod under henting av objekt: $storageKey i bucket")
        }
        return null
    }

    fun list(keyPrefix: String) : List<String> {
        return gcpStorage.list(bucketname , Storage.BlobListOption.prefix(keyPrefix))?.values?.map { v -> v.name}  ?:  emptyList()
    }
}