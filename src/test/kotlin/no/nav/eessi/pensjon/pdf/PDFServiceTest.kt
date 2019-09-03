package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.eessi.pensjon.models.SedType
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows


class PDFServiceTest {

    val pdfService = PDFService()

    @Test
    fun `Gitt en json dokument uten vedlegg så konverter til json dokument med pdf innhold`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))
        val (supported, unsupported) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"}]", supported)
        assertEquals(null, unsupported)
    }

    @Test
    fun `Gitt en json dokument med vedlegg så konverter til json dokument med pdf innhold`() {

        mockkObject(ImageConverter)
        every { ImageConverter.toBase64PDF( any() ) } returns "MockPDFContent"

        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedVedlegg.json")))
        val (supported, unsupported) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"jpg.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"png.pdf\"}]", supported)
        assertEquals(null, unsupported)

        unmockkAll()
    }

    @Test
    fun `Gitt en json dokument med manglende filnavn så kastes MissingKotlinParameterException`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedManglendeFilnavn.json")))
        assertThrows<MissingKotlinParameterException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }

    @Test
    fun `Gitt en json dokument med manglende innhold så kastes MissingKotlinParameterException`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedManglendeInnhold.json")))
        assertThrows<MissingKotlinParameterException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }

    @Test
    fun `Gitt en json dokument med manglende mimeType så blir den satt på listen over uSupporterte vedlegg`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedManglendeMimeType.json")))
        val (_, uSpporterteVedlegg) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        assertEquals("[\"ManglendeMimeType.png\"]", uSpporterteVedlegg)
    }

    @Test
    fun `Gitt en json dokument med ugyldig filnavn så kastes MismatchedInputException`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedUgyldigFilnavn.json")))
        assertThrows<MismatchedInputException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }

    @Test
    fun `Gitt en json dokument med ugyldig innhold så blir den satt på listen over uSupporterte vedlegg`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedUgyldigInnhold.json")))
        val (test, uSpporterteVedlegg) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        assertEquals("[\"UgyldigInnhold.jpg\"]", uSpporterteVedlegg)
    }

    @Test
    fun `Gitt en json dokument med ugyldig MimeType så kastes InvalidFormatException`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedUgyldigMimeType.json")))
        assertThrows<InvalidFormatException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }

    @Test
    fun `Gitt en json dokument med ugyldige nøkkler så kastes MissingKotlinParameterException`() {
        val fileContent = String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedUgyldigNokkler.json")))
        assertThrows<MissingKotlinParameterException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }
}
