package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

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
        logger.debug("sjekker om $storageKey finnes i bucket: $bucketname")
        val obj = gcpStorage.get(BlobId.of(bucketname, storageKey))

        kotlin.runCatching {
            obj.exists()
        }.onFailure {
        }.onSuccess {
            return true
        }
        return false
    }

    fun list(keyPrefix: String) : List<String> {
        return gcpStorage.list(bucketname , Storage.BlobListOption.prefix(keyPrefix))?.values?.map { v -> v.name}  ?:  emptyList()
    }
}