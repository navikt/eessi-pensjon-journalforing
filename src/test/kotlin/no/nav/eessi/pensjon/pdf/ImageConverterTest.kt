package no.nav.eessi.pensjon.pdf

import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Test
import java.util.*
import java.io.File
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.assertThrows


class ImageConverterTest {

    @Test
    fun `Gitt en gyldig png fil når konverterer til pdf så konverter til pdf med jpeg bilde innhold`() {
        val fileContent = FileUtils.readFileToByteArray(File("src/test/resources/documentconverter/navlogo.png"))
        val encodedString = Base64.getEncoder().encodeToString(fileContent)
        val pdf = ImageConverter.toBase64PDF(encodedString)

        assertNotNull(pdf)
        assertNotEquals(encodedString, pdf)
    }

    @Test
    fun `Gitt en korrupt png fil når konverterer til pdf så kast exception`() {
        val fileContent = FileUtils.readFileToByteArray(File("src/test/resources/documentconverter/korruptnavlogo.png"))
        val encodedString = Base64.getEncoder().encodeToString(fileContent)
        assertThrows<Exception> {
            ImageConverter.toBase64PDF(encodedString)
        }
    }
}
