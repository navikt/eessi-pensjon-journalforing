package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.eessi.pensjon.TestUtils
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.SedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class PDFServiceTest {

    private val pdfService = PDFService()

    @BeforeEach
    fun init() {
        pdfService.initMetrics()
    }

    @Test
    fun `Gitt en json dokument uten vedlegg saa konverter til json dokument med pdf innhold`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseUtenVedlegg.json")
        val (supported, unsupported) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"}]", supported)
        assertEquals(0, unsupported.size)
    }

    @Test
    fun `Gitt en json dokument med vedlegg saa konverter til json dokument med pdf innhold`() {

        mockkObject(ImageConverter)
        every { ImageConverter.toBase64PDF( any() ) } returns "MockPDFContent"

        val fileContent = TestUtils.getResource("pdf/pdfResponseMedVedlegg.json")
        val (supported, unsupported) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"jpg.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"png.pdf\"}]", supported)
        assertEquals(0, unsupported.size)

        unmockkAll()
    }

    @Test
    fun `Gitt en json dokument med manglende innhold saa kastes MissingKotlinParameterException`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseMedManglendeInnhold.json")
        assertThrows<MissingKotlinParameterException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }

    @Test
    fun `Gitt en json dokument med manglende mimeType saa blir den satt paa listen over uSupporterte vedlegg`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseMedManglendeMimeType.json")
        val (_, uSpporterteVedlegg) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        assertEquals(1, uSpporterteVedlegg.size)
    }

    @Test
    fun `Gitt en json dokument med ugyldig filnavn saa kastes MismatchedInputException`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseMedUgyldigFilnavn.json")
        assertThrows<MismatchedInputException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }
    @Test
    fun `Gitt en sed med et vedlegg uten filnavn saa opprettes et løpenavn`() {
        val fileContent = TestUtils.getResource("pdf/pdfResonseMedP2000MedVedlegg.json")
        val (supportereVedlegg, usupportertVedlegg) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)

        assertTrue ( supportereVedlegg.contains("P2000_vedlegg_1.pdf"))
        assertEquals(0, usupportertVedlegg.size)

    }

    @Test
    fun `Gitt en sed med et vedlegg uten filnavn og uten mimetype saa legges til unsupportertVedlegg`() {
        val fileContent = SedDokumenter(EuxDokument("P2000",MimeType.PDF, "dokInnhold"),
                listOf(EuxDokument("filnavn", null, "dokInnhold"))).toJson()

        val (_, usupportertVedlegg) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)

        assertEquals(1, usupportertVedlegg.size)
        assertEquals("filnavn", usupportertVedlegg[0].filnavn)
    }

    @Test
    fun `Gitt en json dokument med ugyldig innhold saa blir den satt paa listen over uSupporterte vedlegg`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseMedUgyldigInnhold.json")
        val (_, uSupporterteVedlegg) = pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        assertEquals(1, uSupporterteVedlegg.size)
        assertEquals("UgyldigInnhold.jpg", uSupporterteVedlegg[0].filnavn)
    }

    @Test
    fun `Gitt en json dokument med ugyldig MimeType saa returneres det ugyldige dokumentet i usupporterteVedlegg`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseMedUgyldigMimeType.json")
            val docs = pdfService.parseJsonDocuments(fileContent, SedType.P2000)
            assertNotNull(docs.second)
            assertNull(docs.second[0].mimeType)
    }

    @Test
    fun `Gitt en json dokument med ugyldige noekkler saa kastes MissingKotlinParameterException`() {
        val fileContent = TestUtils.getResource("pdf/pdfResponseMedUgyldigNokkler.json")
        assertThrows<MissingKotlinParameterException> {
            pdfService.parseJsonDocuments(fileContent, SedType.P2000)
        }
    }
}
