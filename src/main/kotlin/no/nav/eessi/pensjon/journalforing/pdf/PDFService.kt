package no.nav.eessi.pensjon.journalforing.pdf

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.metrics.counter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.RuntimeException

/**
 *  Konverterer vedlegg fra bildeformat til pdf
 */
@Service
class PDFService {

    private val logger = LoggerFactory.getLogger(PDFService::class.java)
    private val mapper: ObjectMapper = jacksonObjectMapper()

    private final val pdfConverterNavn = "eessipensjon_journalforing.pdfConverter"
    private val pdfConverterNavnVellykkede = counter(pdfConverterNavn, "vellykkede")
    private val pdfConverterNavnFeilede = counter(pdfConverterNavn, "feilede")

    fun parseJsonDocuments(json: String, sedId: String?): Pair<String, String?>{
        try {
            val documents = mapper.readValue(json, SedDokumenter::class.java)
            val convertedDocuments = listOf(documents.sed).plus(documents.vedlegg ?: listOf())
                    .map { convert(it) }

            val (supportedDocuments, unsupportedDocuments) = convertedDocuments
                    .partition { it.mimeType == MimeType.PDF || it.mimeType == MimeType.PDFA }

            val unnsupportedDocumentsJson = if (unsupportedDocuments.isNotEmpty()) {
                mapper.writeValueAsString(unsupportedDocuments.map { it.filnavn })
            } else {
                null
            }

            val supportedDocumentsJson = if (supportedDocuments.isNotEmpty()) {
                mapper.writeValueAsString(supportedDocuments.map {
                    JournalPostDokument(
                            brevkode = sedId,
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
                pdfConverterNavnFeilede.increment()
                throw RuntimeException("No supported documents, $json")
            }
            pdfConverterNavnVellykkede.increment()
            return Pair(supportedDocumentsJson, unnsupportedDocumentsJson)
        } catch (ex: RuntimeException) {
            pdfConverterNavnFeilede.increment()
            logger.error("RuntimeException: Noe gikk galt under parsing av json, $json", ex)
            throw ex
        } catch (ex: Exception) {
            pdfConverterNavnFeilede.increment()
            logger.error("Noe gikk galt under parsing av json, $json", ex)
            throw ex
        }
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