package no.nav.eessi.pensjon.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO


// TODO using exceptions as control flow is pretty iffy ...
object ImageConverter {

    private val logger: Logger by lazy { LoggerFactory.getLogger(ImageConverter::class.java) }
    private val margin = 30f

    fun toBase64PDF(base64ImageContent: String): String {
        try {
            val awtImage = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(base64ImageContent)))
                    ?: throw RuntimeException("Klarte ikke å dekode bilde")
            ByteArrayOutputStream().use { outStream ->
                PDDocument().use { doc ->
                    val pdImageXObject = LosslessFactory.createFromImage(doc, awtImage)
                    val page = PDPage()

                    val (imageWidth, imageHeight) = pairImageSize(pdImageXObject)
                    val (pageWidth, pageHeight) = getPageDimensions(rotate(page.rotation), page.mediaBox)
                    val (centerX, centerY) = centerCoordinates(rotate(page.rotation), pageHeight, pageWidth)
                    val scale = getScale(imageWidth, pageWidth)
                    val (offsetX, offsetY) = getOffsetPair(imageWidth, imageHeight)

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

    private fun pairImageSize(pdImageXObject: PDImageXObject): Pair<Int, Int> {
        val imageWidth = pdImageXObject.width
        val imageHeight = pdImageXObject.height
        return Pair(imageWidth, imageHeight)
    }

    private fun rotate(rotation: Int): Boolean  = rotation == 90 || rotation == 270
    
    private fun getScale(imageWidth: Int, pageWidth: Float): Float {
        val contentWidth = pageWidth - 2 * margin
        return if (imageWidth > contentWidth) contentWidth / imageWidth else 1.0f
    }

    private fun getOffsetPair(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        val offsetX = imageWidth / 2
        val offsetY = imageHeight / 2
        return Pair(offsetX, offsetY)
    }

    private fun getPageDimensions(
        rotate: Boolean,
        pageSize: PDRectangle
    ): Pair<Float, Float> {
        val pageWidth = if (rotate) pageSize.height else pageSize.width
        val pageHeight = if (rotate) pageSize.width else pageSize.height
        return Pair(pageWidth, pageHeight)
    }

    private fun centerCoordinates(
        rotate: Boolean,
        pageHeight: Float,
        pageWidth: Float
    ): Pair<Float, Float> {
        val centerX = if (rotate) pageHeight / 2f else pageWidth / 2f
        val centerY = if (rotate) pageWidth / 2f else pageHeight / 2f
        return Pair(centerX, centerY)
    }

}
