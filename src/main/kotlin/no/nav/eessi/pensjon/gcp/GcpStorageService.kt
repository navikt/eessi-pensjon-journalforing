package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.OMSORG
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_NAME_GJENNY}") var gjennyBucket: String,
    var gcpStorage: Storage
) {
    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        ensureBucketExists(gjennyBucket)
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
    fun hentBlobId(bucketName: String, storageKey: String): BlobId =
        gcpStorage.get(BlobId.of(bucketName, storageKey)).blobId

    fun hentFraGjenny(storageKey: String): String? {
        logger.debug("Henter gjennydetaljer for rinaSakId: $storageKey")
        return hent(storageKey, gjennyBucket)
    }

    fun hentIndex(): String? {
        logger.debug("Henter index")
        return hent("journalpostindex", gjennyBucket)
    }

    fun hent(storageKey: String, bucketName: String): String? {
        try {
            logger.info("storageKey: $storageKey og bucketName: $bucketName")
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

    fun lagreJournalPostIndex(index: String) {
        val blobInfo =  BlobInfo.newBuilder(BlobId.of(gjennyBucket, "journalpostindex")).setContentType("application/json").build()
        kotlin.runCatching {
            gcpStorage.writer(blobInfo).use { it.write(ByteBuffer.wrap(index.toByteArray())) }.also { logger.info("Lagrer index for journalpost: $it") }
        }.onFailure { e ->
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", e)
        }.onSuccess {
        }
    }

    fun lagre(euxCaseId: String, gjennysak: GjennySak? = null) {
        if (eksisterer(euxCaseId, gjennyBucket)) return
        val blobInfo =  BlobInfo.newBuilder(BlobId.of(gjennyBucket, euxCaseId)).setContentType("application/json").build()
        kotlin.runCatching {
            gcpStorage.writer(blobInfo).use { it.write(ByteBuffer.wrap(gjennysak?.toJson()?.toByteArray())) }.also { logger.info("Lagret info på S3 med rinaID: $it") }
        }.onFailure { e ->
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", e)
        }.onSuccess {
            if (gjennysak != null) logger.info("Lagret info på S3 med rinaID: $euxCaseId for gjenny: ${gjennysak.toJson()}")
        }
    }

    fun oppdaterGjennysak(sedHendelse: SedHendelse, gcpstorageObject: GjennySak, gjennysakFraSed: String) : String {
        val blobId = hentBlobId(gjennyBucket, sedHendelse.rinaSakId)
        slettJournalpostDetaljer(blobId)
        logger.warn("Gjennysak finnes for rinaSakId: ${sedHendelse.rinaSakId}")

        val saksType = if (gcpstorageObject.sakType == "OMSORG") OMSORG else BARNEP
        lagre(sedHendelse.rinaSakId, GjennySak(gjennysakFraSed, saksType.name))
        return saksType.name
    }

    fun slettJournalpostDetaljer(blobId: BlobId) {
        try {
            logger.info("Sletter journalpostdetaljer for rinaSakId: $blobId")
            gcpStorage.delete(blobId).also { logger.info("Slett av journalpostdetaljer utført: $it") }
        } catch (ex: Exception) {
            logger.warn("En feil oppstod under sletting av objekt: $blobId i bucket")
        }
    }
}
