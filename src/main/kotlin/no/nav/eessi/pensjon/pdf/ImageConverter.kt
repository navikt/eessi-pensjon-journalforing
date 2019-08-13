package no.nav.eessi.pensjon.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.RuntimeException
import java.util.*
import javax.imageio.ImageIO


// TODO using exceptions as control flow is pretty iffy ...
object ImageConverter {

    private val logger: Logger by lazy { LoggerFactory.getLogger(ImageConverter::class.java) }

    fun toBase64PDF(base64ImageContent: String): String {
        try {
            val awtImage = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(base64ImageContent)))
                    ?: throw RuntimeException("Klarte ikke å dekode bilde")
            ByteArrayOutputStream().use { outStream ->
                PDDocument().use { doc ->
                    val pdImageXObject = LosslessFactory.createFromImage(doc, awtImage)
                    val imageWidth = pdImageXObject.width
                    val imageHeight = pdImageXObject.height

                    val page = PDPage()
                    val pageSize = page.mediaBox
                    val rotation = page.rotation
                    val rotate = rotation == 90 || rotation == 270
                    val pageWidth = if (rotate) pageSize.height else pageSize.width
                    val pageHeight = if (rotate) pageSize.width else pageSize.height
                    val margin = 30f
                    val contentWidth = pageWidth - 2 * margin
                    val centerX = if (rotate) pageHeight / 2f else pageWidth / 2f
                    val centerY = if (rotate) pageWidth / 2f else pageHeight / 2f

                    val scale = if (imageWidth > contentWidth) contentWidth / imageWidth else 1.0f

                    val offsetX = imageWidth / 2
                    val offsetY = imageHeight / 2

                    PDPageContentStream(doc, page).use {
                        it.drawImage(
                                pdImageXObject,
                                (centerX - offsetX * scale),
                                (centerY - offsetY * scale),
                                imageWidth * scale,
                                imageHeight * scale)
                    }

                    doc.addPage(page)
                    doc.save(outStream)
                }
                return String(Base64.getEncoder().encode(outStream.toByteArray()))
            }
        } catch (ex: Exception) {
            logger.error("Klarte ikke å konvertere dokument: $ex", ex)
            throw ex
        }
    }

}
