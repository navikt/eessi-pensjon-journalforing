package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.RuntimeException


val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

/**
 * Konverterer vedlegg fra bildeformat til pdf
 *
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class PDFService(@Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {
    private val logger = LoggerFactory.getLogger(PDFService::class.java)

    fun parseJsonDocuments(json: String, sedType: SedType): Pair<String, List<EuxDokument>>{
        return metricsHelper.measure("pdfConverter") {
            try {
                val documents = mapper.readValue(json, SedDokumenter::class.java)
                val sedDokument = konverterTilLesbartFilnavn(sedType, documents)
                val convertedDocuments = listOf(sedDokument).plus(documents.vedlegg ?: listOf())
                        .map { convert(it) }

                val (supportedDocuments, unsupportedDocuments) = convertedDocuments
                        .partition { it.mimeType == MimeType.PDF || it.mimeType == MimeType.PDFA }

                val unnsupportedDocumentsJson = if (unsupportedDocuments.isNotEmpty()) {
                    unsupportedDocuments
                } else {
                    emptyList()
                }

                val supportedDocumentsJson = if (supportedDocuments.isNotEmpty()) {
                    mapper.writeValueAsString(supportedDocuments.map {
                        JournalPostDokument(
                                brevkode = sedType.name,
                                dokumentKategori = "SED",
                                dokumentvarianter = listOf(
                                        Dokumentvarianter(
                                                filtype = it.mimeType?.decode()
                                                        ?: throw RuntimeException("MimeType is null after being converted to PDF, $it"),
                                                fysiskDokument = it.innhold,
                                                variantformat = Variantformat.ARKIV
                                        )),
                                tittel = it.filnavn)
                    })
                } else {
                    throw RuntimeException("No supported documents, $json")
                }
                Pair(supportedDocumentsJson, unnsupportedDocumentsJson)
            } catch (ex: RuntimeException) {
                logger.error("RuntimeException: Noe gikk galt under parsing av json, $json", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Noe gikk galt under parsing av json, $json", ex)
                throw ex
            }
        }
    }

    /**
     *  Konverterer SED filnavn til menneskelesbart filnavn
     *
     *  fra format: P2200_f899bf659ff04d20bc8b978b186f1ecc_1
     *  til format: P2200.pdf
     */
    private fun konverterTilLesbartFilnavn(sedType: SedType, documents: SedDokumenter): EuxDokument {
        return EuxDokument("$sedType.pdf", documents.sed.mimeType, documents.sed.innhold)
    }

    private fun convert(document: EuxDokument): EuxDokument{
        return when (document.mimeType){
            null -> document
            MimeType.PDF -> document
            MimeType.PDFA -> document
            else -> try {
                EuxDokument(
                        filnavn = konverterFilendingTilPdf(document.filnavn),
                        mimeType = MimeType.PDF,
                        innhold = ImageConverter.toBase64PDF(document.innhold)
                )
            } catch (ex: Exception){
                document
            }
        }
    }

    private fun konverterFilendingTilPdf(filnavn: String): String {
        return filnavn.replaceAfterLast(".", "pdf")
    }
}
