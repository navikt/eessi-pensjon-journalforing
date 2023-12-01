package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.document.MimeType
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class PDFServiceTest {

    private val dokumentHelper: EuxService = mockk()

    private val pdfService = PDFService(dokumentHelper)

    @Test
    fun `Gitt en json dokument uten vedlegg saa konverter til json dokument med pdf innhold`() {
        val fileContent = javaClass.getResource("/pdf/pdfResponseUtenVedlegg.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val (supported, unsupported) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"}]", supported)
        assertEquals(0, unsupported.size)
    }

    @Test
    fun `Gitt en json dokument med vedlegg saa konverter til json dokument med pdf innhold`() {

        mockkObject(ImageConverter)
        every { ImageConverter.toBase64PDF( any() ) } returns "MockPDFContent"

        val fileContent = javaClass.getResource("/pdf/pdfResponseMedVedlegg.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val (supported, unsupported) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"jpg.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"png.pdf\"}]", supported)
        assertEquals(0, unsupported.size)

        unmockkAll()
    }

    @Test
    fun `Gitt en json dokument med tomt vedlegg saa konvertes den til et usupportert vedlegg`() {

        mockkObject(ImageConverter)
        every { ImageConverter.toBase64PDF( any() ) } returns "MockPDFContent"

        val fileContent = javaClass.getResource("/pdf/pdfResponseMedTomtVedlegg.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val (_, unsupported) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)

        assertEquals(1, unsupported.size)

        unmockkAll()
    }

    @Test
    fun `Gitt en json dokument med manglende mimeType saa blir den satt paa listen over uSupporterte vedlegg`() {
        val fileContent = javaClass.getResource("/pdf/pdfResponseMedManglendeMimeType.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val (_, uSpporterteVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)
        assertEquals(1, uSpporterteVedlegg.size)
    }

    @Test
    fun `Feil som kastes fra euxService skal ikke fanges`() {
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } throws mockk<MismatchedInputException>()

        assertThrows<MismatchedInputException> {
            pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)
        }
    }

    @Test
    fun `Manglende dokument fra euxService skal kaste exception`() {
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns null

        assertThrows<RuntimeException> {
            pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)
        }
    }

    @Test
    fun `Gitt en sed med et vedlegg uten filnavn saa opprettes et løpenavn`() {
        val fileContent = javaClass.getResource("/pdf/pdfResonseMedP2000MedVedlegg.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val (supportereVedlegg, usupportertVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)

        assertTrue ( supportereVedlegg.contains("P2000_vedlegg_1.pdf"))
        assertEquals(0, usupportertVedlegg.size)

    }

    @Test
    fun `Gitt en sed med et vedlegg uten filnavn og uten mimetype saa legges til unsupportertVedlegg`() {
        val fileContent = SedDokumentfiler(
            SedVedlegg("P2000", MimeType.PDF, "dokInnhold"),
            listOf(SedVedlegg("filnavn", null, "dokInnhold"))
        )
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns fileContent

        val (_, usupportertVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)

        assertEquals(1, usupportertVedlegg.size)
        assertEquals("filnavn", usupportertVedlegg[0].filnavn)
    }

    @Test
    fun `Gitt en json dokument med ugyldig innhold saa blir den satt paa listen over uSupporterte vedlegg`() {
        val fileContent = javaClass.getResource("/pdf/pdfResponseMedUgyldigInnhold.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val (_, uSupporterteVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)
        assertEquals(1, uSupporterteVedlegg.size)
        assertEquals("UgyldigInnhold.jpg", uSupporterteVedlegg[0].filnavn)
    }

    @Test
    fun `Gitt en json dokument med ugyldig MimeType saa returneres det ugyldige dokumentet i usupporterteVedlegg`() {
        val fileContent =
            javaClass.getResource("/pdf/pdfResponseMedUgyldigMimeType.json").readText()
        every { dokumentHelper.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent)

        val docs = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", P2000)
        assertNotNull(docs.second)
        assertNull(docs.second[0].mimeType)
    }
}
