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
import javax.annotation.PostConstruct


val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

/**
 * Konverterer vedlegg fra bildeformat til pdf
 *
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class PDFService(@Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {
    private val logger = LoggerFactory.getLogger(PDFService::class.java)

    private lateinit var pdfConverter: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        pdfConverter = metricsHelper.init("pdfConverter")
    }

    fun parseJsonDocuments(json: String, sedType: SedType): Pair<String, List<EuxDokument>> {
        return pdfConverter.measure {
            try {
                val documents = mapper.readValue(json, SedDokumenter::class.java)
                val hovedDokument = EuxDokument("$sedType.pdf", documents.sed.mimeType, documents.sed.innhold)
                val vedlegg = (documents.vedlegg ?: listOf())
                        .mapIndexed { index, vedlegg ->
                            if (vedlegg.filnavn == null) {
                                EuxDokument(genererFilnavn(sedType, index, vedlegg)
                                        , vedlegg.mimeType
                                        , vedlegg.innhold)
                            } else {
                                vedlegg
                            }
                        }
                        .map { konverterEventuelleBilderTilPDF(it) }
                val convertedDocuments = listOf(hovedDokument).plus(vedlegg)

                logger.info("SED omfatter ${convertedDocuments.size} dokumenter, inkludert vedlegg")

                val (supportedDocuments, unsupportedDocuments) = convertedDocuments.partition {
                    (it.mimeType == MimeType.PDF || it.mimeType == MimeType.PDFA) && it.filnavn != null
                }

                logger.info("SED omfatter ${unsupportedDocuments.size} dokumenter som vi ikke har klart å konvertere til PDF")
                unsupportedDocuments.forEach {
                    logger.info("Usupportert dokument: ${it.filnavn} - av type${it.mimeType}")
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

                Pair(supportedDocumentsJson, unsupportedDocuments)
            } catch (ex: RuntimeException) {
                logger.error("RuntimeException: Noe gikk galt under parsing av json, $json", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Noe gikk galt under parsing av json, $json", ex)
                throw ex
            }
        }
    }


    private fun genererFilnavn(sedType: SedType, index: Int, vedlegg: EuxDokument): String? {
        return if (vedlegg.mimeType != null) {
            "${sedType.name}_vedlegg_${index+1}.${vedlegg.mimeType.decode().toLowerCase()}"
        } else {
            vedlegg.filnavn
        }
    }

    private fun konverterEventuelleBilderTilPDF(document: EuxDokument): EuxDokument {
        return when (document.mimeType) {
            null -> document
            MimeType.PDF -> document
            MimeType.PDFA -> document
            else -> try {
                EuxDokument(
                        filnavn = konverterFilendingTilPdf(document.filnavn!!),
                        mimeType = MimeType.PDF,
                        innhold = ImageConverter.toBase64PDF(document.innhold)
                )
            } catch (ex: Exception) {
                document
            }
        }
    }

    private fun konverterFilendingTilPdf(filnavn: String): String {
        return filnavn.replaceAfterLast(".", "pdf")
    }
}
