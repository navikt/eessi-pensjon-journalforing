package no.nav.eessi.pensjon.journalforing.pdf

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.MimeType
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

/**
 * Konverterer vedlegg fra bildeformat til pdf
 *
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class PDFService(
    private val euxService: EuxService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val maxTotalBase64SizeBytes: Long = 150L * 1024 * 1024
    private val BYTES_PER_MB = 1024 * 1024
    private val logger = LoggerFactory.getLogger(PDFService::class.java)

    private var pdfConverter: MetricsHelper.Metric

    init {
        pdfConverter = metricsHelper.init("pdfConverter")
    }

    fun hentDokumenterOgVedlegg(rinaSakId: String, dokumentId: String, sedType: SedType): Pair<String, List<SedVedlegg>> {
        val documents = euxService.hentAlleDokumentfiler(rinaSakId, dokumentId)
            ?: throw RuntimeException("Klarte ikke hente dokumenter fra EUX (rinaSakId: $rinaSakId, dokumentId: $dokumentId)")

        return pdfConverter.measure {
            val hovedDokument = SedVedlegg("${sedType.typeMedBeskrivelse()}.pdf", documents.sed.mimeType, documents.sed.innhold)
            val vedlegg = documents.vedlegg.orEmpty()
                .mapIndexed { index, v -> opprettDokument(index, v, sedType) }
                .map { konverterEventuelleBilderTilPDF(it) }

            val alleDokumenter = listOf(hovedDokument) + vedlegg
            val (supported, unsupported) = alleDokumenter.partition { it.erStoettetPdf() }.also { loggUsupporterteDokumenter(it.second) }

            if (supported.isEmpty()) {
                throw RuntimeException("Ingen støttede dokumenter funnet")
            }

            validerVedleggInfo(supported)

            val journalPostDokumenter = supported.map { dok ->
                logger.info("Oppretter journalpostDokument: rinaSakId=$rinaSakId, dokumentId=$dokumentId, sedtype=${sedType.name}, tittel=${dok.filnavn}")
                tilJournalPostDokument(dok, sedType)
            }

            Pair(mapper.writeValueAsString(journalPostDokumenter), unsupported)
        }
    }

    private fun SedVedlegg.erStoettetPdf() = mimeType in listOf(MimeType.PDF, MimeType.PDFA) && filnavn != null && innhold != null

    private fun loggUsupporterteDokumenter(unsupported: List<SedVedlegg>) {
        if (unsupported.isNotEmpty()) {
            logger.info("${unsupported.size} dokumenter kunne ikke konverteres til PDF: ${unsupported.map { "${it.filnavn} (${it.mimeType})" }}")
        }
    }

    private fun tilJournalPostDokument(dok: SedVedlegg, sedType: SedType) = JournalPostDokument(
        brevkode = sedType.name,
        dokumentKategori = "SED",
        dokumentvarianter = listOf(
            Dokumentvarianter(
                filtype = dok.mimeType!!.name,
                fysiskDokument = dok.innhold!!,
                variantformat = Variantformat.ARKIV
            )
        ),
        tittel = dok.filnavn
    )

    private fun estimateDecodedSize(base64: String): Long {
        val padding = base64.takeLastWhile { it == '=' }.length
        return ((base64.length * 3L) / 4L) - padding
    }

    fun validerVedleggInfo(filtrertSupportedDokument: List<SedVedlegg>) {
        try {
            val totalDecodedSize = filtrertSupportedDokument.sumOf { vedlegg ->
                vedlegg.innhold?.let { estimateDecodedSize(it) } ?: 0L
            }
            if (totalDecodedSize > maxTotalBase64SizeBytes) {
                val sizeMb = totalDecodedSize.toDouble() / (BYTES_PER_MB)
                logger.error(
                    "Total størrelse på dokumentene (${String.format("%.2f", sizeMb)} MB) " +
                            "overskrider grensen på ${maxTotalBase64SizeBytes / (BYTES_PER_MB)} MB"
                )
            }
        } catch (e: Exception) {
            logger.warn("Feil under validering av vedlegg", e)
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
            "${sedType.name}_vedlegg_${index+1}.${vedlegg.mimeType?.name?.lowercase()}"
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

    fun dokumentStorrelse(input: String?): String {
        if (input == null) return "0.0"
        val decodedSize = estimateDecodedSize(input)
        val sizeMb = decodedSize.toDouble() / BYTES_PER_MB
        return String.format("%.2f", sizeMb)
    }

    fun usupporterteFilnavn(uSupporterteVedlegg: List<SedVedlegg>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }

}
