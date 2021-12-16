package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.MimeType
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct


val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

/**
 * Konverterer vedlegg fra bildeformat til pdf
 *
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class PDFService(
    private val euxService: EuxDokumentHelper,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(PDFService::class.java)

    private lateinit var pdfConverter: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        pdfConverter = metricsHelper.init("pdfConverter")
    }

    fun hentDokumenterOgVedlegg(rinaSakId: String, dokumentId: String, sedType: SedType): Pair<String, List<SedVedlegg>> {
        val documents = euxService.hentAlleDokumentfiler(rinaSakId, dokumentId)
            ?: throw RuntimeException("Failed to get documents from EUX (rinaSakId: $rinaSakId, dokumentId: $dokumentId)")

        return pdfConverter.measure {
            try {
                val hovedDokument = SedVedlegg("${sedType.typeMedBeskrivelse()}.pdf", documents.sed.mimeType, documents.sed.innhold)
                val vedlegg = (documents.vedlegg ?: listOf())
                        .mapIndexed { index, vedlegg -> opprettDokument(index, vedlegg, sedType) }
                        .map { konverterEventuelleBilderTilPDF(it) }
                val convertedDocuments = listOf(hovedDokument).plus(vedlegg)

                logger.info("SED omfatter ${convertedDocuments.size} dokumenter, inkludert vedlegg")

                val (supportedDocuments, unsupportedDocuments) = convertedDocuments.partition {
                    (it.mimeType == MimeType.PDF || it.mimeType == MimeType.PDFA) && it.filnavn != null && it.innhold != null
                }

                logger.info("SED omfatter ${unsupportedDocuments.size} dokumenter som vi ikke har klart å konvertere til PDF")
                unsupportedDocuments.forEach {
                    logger.info("Usupportert dokument: ${it.filnavn} - av type${it.mimeType}")
                }
                val supportedDocumentsJson = if (supportedDocuments.isNotEmpty()) {
                    val filtrertSupportedDokument = supportedDocuments.filterNot { it.innhold == null }
                    mapper.writeValueAsString(filtrertSupportedDokument.map {
                        logger.info("Oppretter journalpostDokument for rinaSakId: $rinaSakId, dokumentId: $dokumentId med: sedtype: ${sedType.name} , tittel: ${it.filnavn}")
                        JournalPostDokument(
                                brevkode = sedType.name,
                                dokumentKategori = "SED",
                                dokumentvarianter = listOf(
                                        Dokumentvarianter(
                                                filtype = it.mimeType?.name
                                                        ?: throw RuntimeException("MimeType is null after being converted to PDF, $it"),
                                                fysiskDokument = it.innhold!!,
                                                variantformat = Variantformat.ARKIV
                                        )),
                                tittel = it.filnavn)
                    })
                } else {
                    throw RuntimeException("No supported documents, ${documents.toJson()}")
                }

                Pair(supportedDocumentsJson, unsupportedDocuments)
            } catch (ex: Exception) {
                logger.error("Noe gikk galt under konvertering av vedlegg til PDF", ex)
                throw ex
            }
        }
    }

    private fun opprettDokument(index: Int, vedlegg: SedVedlegg, sedType: SedType): SedVedlegg {
        if (vedlegg.filnavn != null)
            return vedlegg

        return SedVedlegg(
            genererFilnavn(sedType, index, vedlegg),
            vedlegg.mimeType,
            vedlegg.innhold
        )
    }

    private fun genererFilnavn(sedType: SedType, index: Int, vedlegg: SedVedlegg): String? {
        return if (vedlegg.mimeType != null) {
            "${sedType.name}_vedlegg_${index+1}.${vedlegg.mimeType?.name?.toLowerCase()}"
        } else {
            vedlegg.filnavn
        }
    }

    private fun konverterEventuelleBilderTilPDF(document: SedVedlegg): SedVedlegg {
        return when (document.mimeType) {
            null -> document
            MimeType.PDF -> document
            MimeType.PDFA -> document
            else -> try {
                SedVedlegg(
                        filnavn = konverterFilendingTilPdf(document.filnavn!!),
                        mimeType = MimeType.PDF,
                        innhold = ImageConverter.toBase64PDF(document.innhold!!)
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
