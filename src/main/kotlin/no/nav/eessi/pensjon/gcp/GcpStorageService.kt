package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.OMSORG
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.util.Base64

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_NAME_GJENNY}") var gjennyBucket: String,
    @param:Value("\${GCP_BUCKET_NAME_VEDLEGG}") var vedleggBucket: String,
    var gcpStorage: Storage
) {
    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        ensureBucketExists(gjennyBucket)
        ensureBucketExists(vedleggBucket)
    }

    private fun ensureBucketExists(bucketName: String) {
        if (gcpStorage.get(bucketName) == null) {
            throw IllegalStateException("Fant ikke bucket med navn $bucketName. Må provisjoneres")
        }
        logger.info("Bucket $bucketName funnet.")
    }

    fun gjennyFinnes(storageKey: String): Boolean = eksisterer(storageKey, gjennyBucket)

    private fun eksisterer(storageKey: String, bucketName: String): Boolean {
        val obj = gcpStorage.get(BlobId.of(bucketName, storageKey))
        return try {
            val exists = obj.exists()
            if (exists) logger.info("Henter melding for $storageKey fra $bucketName")
            exists
        } catch (e: Exception) {
            logger.info("Melding for $storageKey finnes ikke for $bucketName")
            false
        }
    }

    fun hentBlobId(bucketName: String, storageKey: String): BlobId =
        gcpStorage.get(BlobId.of(bucketName, storageKey)).blobId

    fun hentFraGjenny(storageKey: String): String? {
        logger.info("Henter gjennydetaljer for rinaSakId: $storageKey")
        return hent(storageKey, gjennyBucket)
    }

    fun hent(storageKey: String, bucketName: String): String? {
        return try {
            val jsonHendelse = gcpStorage.get(BlobId.of(bucketName, storageKey))
            if (jsonHendelse.exists()) {
                logger.info("Henter melding med rinanr $storageKey, for bucket $bucketName")
                jsonHendelse.getContent().decodeToString()
            } else null
        } catch (ex: Exception) {
            null
        }
    }

    fun lagre(euxCaseId: String, gjennysak: GjennySak? = null) {
        if (eksisterer(euxCaseId, gjennyBucket)) return
        val blobInfo = BlobInfo.newBuilder(BlobId.of(gjennyBucket, euxCaseId)).setContentType("application/json").build()
        try {
            if (gjennysak?.sakId == null || (gjennysak.sakId.length == 5 && gjennysak.sakId.any { it.isDigit() })) {
                gcpStorage.writer(blobInfo).use { it.write(ByteBuffer.wrap(gjennysak?.toJson()?.toByteArray())) }
                val gjennysakObj = gcpStorage.get(BlobId.of(gjennyBucket, euxCaseId))
                if (eksisterer(euxCaseId, gjennyBucket)) logger.info("Lagret gjennysak $gjennysakObj til gcp bucket")
                else throw RuntimeException("Obs! Fikk ikke lagret gjennysak $gjennysakObj til gcp bucket")
            } else {
                throw IllegalArgumentException("SakId må være korrekt strukturert med 5 tegn; mottok: ${gjennysak.toJson()}")
            }
            if (gjennysak != null) logger.info("Lagret info på S3 med rinaID: $euxCaseId for gjenny: ${gjennysak.toJson()}")
        } catch (e: Exception) {
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", e)
        }
    }

    data class DokumentInfo(val filnavn: String, val storrelse: String)

    fun dokumentStorrelse(input: String?): String {
        if (input.isNullOrEmpty()) return "0.0"
        return try {
            val bytes = Base64.getDecoder().decode(input)
            val sizeMb = bytes.size / (1024.0 * 1024.0)
            String.format("%.2f", sizeMb)
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun lagreVedleggInfo(rinaId: String, sedId: String, vedleggsInfoListe: List<SedVedlegg>?) {
        val sedPath = "$rinaId/$sedId"
        val blobInfo = BlobInfo.newBuilder(BlobId.of(vedleggBucket, sedPath)).setContentType("application/json").build()
        val dokumenterInfo = vedleggsInfoListe?.mapNotNull {
            val filnavn = it.filnavn?.takeIf { navn -> navn.isNotBlank() } ?: return@mapNotNull null
            DokumentInfo(
                filnavn = filnavn,
                storrelse = dokumentStorrelse(it.innhold)
            )
        }
        logger.debug("Lagrer vedleggsinfo for $dokumenterInfo")
        try {
            gcpStorage.writer(blobInfo).use { it.write(ByteBuffer.wrap(dokumenterInfo?.toJson()?.toByteArray())) }
            val gjennysak = gcpStorage.get(BlobId.of(vedleggBucket, sedPath))
            if (eksisterer(sedPath, vedleggBucket)) logger.info("Lagret gjennysak $gjennysak til gcp bucket")
            else throw RuntimeException("Obs! Fikk ikke lagret gjennysak $gjennysak til gcp bucket")
            logger.info("Lagret info på S3 med rinaID: $rinaId for sedId: $sedId for vedlegginfo: $vedleggsInfoListe")
        } catch (e: Exception) {
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", e)
        }
    }

    fun oppdaterGjennysak(sedHendelse: SedHendelse, gcpstorageObject: GjennySak, gjennysakFraSed: String): String {
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
