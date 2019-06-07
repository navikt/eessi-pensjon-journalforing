package no.nav.eessi.pensjon.journalforing.services.documentconverter

import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import org.springframework.stereotype.Service
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

@Service
class DocumentConverterService {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PdfService::class.java) }

    fun konverterFraBildeTilBase64EncodedPDF(convertFrom: DocumentConverterModel): String  {
        logger.info("Konverterer dokument fra ${convertFrom.mimeType} til ${MimeType.PDF}")
        val doc: PDDocument?
        doc = PDDocument()
        val page = PDPage()
        val outStream = ByteArrayOutputStream()

        try {
            val awtImage = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(convertFrom.documentContent)))
            val pdImageXObject = LosslessFactory.createFromImage(doc, awtImage)
            addImage(doc,page,pdImageXObject)//contentStream.drawImage(pdImageXObject, 0f, 0f, awtImage.width.toFloat(), awtImage.height.toFloat())
            doc.save(outStream)
        } catch (ex: Exception) {
            logger.error("Klarte ikke å konvertere dokument: ${ex.printStackTrace()}")
            throw ex
        } finally {
            doc.close()
        }
        return String(Base64.getEncoder().encode(outStream.toByteArray()))
    }

    private fun addImage(outputPdf : PDDocument, page: PDPage, image : PDImageXObject) {

        outputPdf.addPage(page)
        val margin = 30f

        // calculate to center of the page
        val pageSize = page.mediaBox
        val rotation = page.rotation
        val rotate = rotation == 90 || rotation == 270
        val pageWidth = if (rotate) pageSize.height else pageSize.width
        val pageHeight = if (rotate) pageSize.width else pageSize.height
        val contentWidth = pageWidth - 2 * margin
        //val contentHeight = pageHeight - 2 * margin
        val imageWidth = image.width
        val imageHeight = image.height
        var scale = 1.0f
        val centerX = if (rotate) pageHeight / 2f else pageWidth / 2f
        val centerY = if (rotate) pageWidth  / 2f else pageHeight / 2f
        val offsetX = imageWidth / 2
        val offsetY = imageHeight / 2

        if (imageWidth > contentWidth) {
            scale = contentWidth / imageWidth
        }

        val cs = PDPageContentStream(outputPdf, page)
        cs.drawImage(image, (centerX - offsetX * scale),(centerY - offsetY * scale), imageWidth * scale, imageHeight * scale)
        cs.close()
    }
}