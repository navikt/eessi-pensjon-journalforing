package no.nav.eessi.pensjon.journalforing.services.documentconverter

import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import org.apache.commons.io.FileUtils
import org.junit.Test
import java.util.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull


class DocumentConverterServiceTest {
    private val converterService = DocumentConverterService()

    @Test
    fun `Gitt en gyldig png fil når konverterer til pdf så konverter til pdf med jpeg bilde innhold`() {
        val fileContent = FileUtils.readFileToByteArray(File("src/test/resources/documentconverter/navlogo.png"))
        val encodedString = Base64.getEncoder().encodeToString(fileContent)
        val convertModel = DokumentConvertererModel(encodedString, MimeType.PNG)
        val pdf = converterService.konverterFraBildeTilBase64EncodedPDF(convertModel)

        assertNotNull(pdf)
        assertNotEquals(encodedString, pdf)
    }

    @Test
    fun `Gitt en gyldig pdf fil når konverterer til pdf så returner innsendt pdf uten å konvertere`() {
        val fileContent = FileUtils.readFileToByteArray(File("src/test/resources/documentconverter/navlogo.pdf"))
        val encodedString = Base64.getEncoder().encodeToString(fileContent)
        val convertModel = DokumentConvertererModel(encodedString, MimeType.PDF)
        val pdf = converterService.konverterFraBildeTilBase64EncodedPDF(convertModel)
        assertNotNull(encodedString)
        assertEquals(encodedString, pdf)
    }

    @Test(expected = Exception::class)
    fun `Gitt en korrupt png fil når konverterer til pdf så kast exception`() {
        val fileContent = FileUtils.readFileToByteArray(File("src/test/resources/documentconverter/korruptnavlogo.png"))
        val encodedString = Base64.getEncoder().encodeToString(fileContent)
        val convertModel = DokumentConvertererModel(encodedString, MimeType.PNG)
        val pdf = converterService.konverterFraBildeTilBase64EncodedPDF(convertModel)
    }
}