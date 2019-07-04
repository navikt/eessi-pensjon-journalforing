package no.nav.eessi.pensjon.journalforing.documentconverter

import no.nav.eessi.pensjon.journalforing.pdf.ImageConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DocumentConverter {

    private val logger: Logger by lazy { LoggerFactory.getLogger(DocumentConverter::class.java) }

    fun convertToBase64PDF(from: MimeDocument): String {
        if (from.mimeType == "application/pdf" || from.mimeType == "application/pdfa"){
            logger.info("Dokumentet er allerede i PDF format, konverteres ikke")
            return from.base64Document
        }
        logger.info("Konverterer dokument fra ${from.mimeType} til application/pdf")
        return ImageConverter.toBase64PDF(from.base64Document)
    }
}

data class MimeDocument(
        val base64Document: String,
        val mimeType: String)
