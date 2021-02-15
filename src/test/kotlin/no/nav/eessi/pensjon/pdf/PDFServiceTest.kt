package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.document.MimeType
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class PDFServiceTest {

    private val euxService = mockk<EuxService>()

    private val pdfService = PDFService(euxService)

    @BeforeEach
    fun init() {
        pdfService.initMetrics()
    }

    @Test
    fun `Gitt en json dokument uten vedlegg saa konverter til json dokument med pdf innhold`() {
        val fileContent = javaClass.getResource("/pdf/pdfResponseUtenVedlegg.json").readText()
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent, typeRefs())

        val (supported, unsupported) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"}]", supported)
        assertEquals(0, unsupported.size)
    }

    @Test
    fun `Gitt en json dokument med vedlegg saa konverter til json dokument med pdf innhold`() {

        mockkObject(ImageConverter)
        every { ImageConverter.toBase64PDF( any() ) } returns "MockPDFContent"

        val fileContent = javaClass.getResource("/pdf/pdfResponseMedVedlegg.json").readText()
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent, typeRefs())

        val (supported, unsupported) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)

        assertEquals("[{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"P2000 - Krav om alderspensjon.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"jpg.pdf\"},{\"brevkode\":\"P2000\",\"dokumentKategori\":\"SED\",\"dokumentvarianter\":[{\"filtype\":\"PDF\",\"fysiskDokument\":\"MockPDFContent\",\"variantformat\":\"ARKIV\"}],\"tittel\":\"png.pdf\"}]", supported)
        assertEquals(0, unsupported.size)

        unmockkAll()
    }

    @Test
    fun `Gitt en json dokument med manglende mimeType saa blir den satt paa listen over uSupporterte vedlegg`() {
        val fileContent = javaClass.getResource("/pdf/pdfResponseMedManglendeMimeType.json").readText()
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent, typeRefs())

        val (_, uSpporterteVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)
        assertEquals(1, uSpporterteVedlegg.size)
    }

    @Test
    fun `Feil som kastes fra EuxService skal ikke fanges`() {
        every { euxService.hentAlleDokumentfiler(any(), any()) } throws mockk<MismatchedInputException>()

        assertThrows<MismatchedInputException> {
            pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)
        }
    }

    @Test
    fun `Manglende dokument fra EuxService skal kaste exception`() {
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns null

        assertThrows<RuntimeException> {
            pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)
        }
    }

    @Test
    fun `Gitt en sed med et vedlegg uten filnavn saa opprettes et løpenavn`() {
        val fileContent = javaClass.getResource("/pdf/pdfResonseMedP2000MedVedlegg.json").readText()
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent, typeRefs())

        val (supportereVedlegg, usupportertVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)

        assertTrue ( supportereVedlegg.contains("P2000_vedlegg_1.pdf"))
        assertEquals(0, usupportertVedlegg.size)

    }

    @Test
    fun `Gitt en sed med et vedlegg uten filnavn og uten mimetype saa legges til unsupportertVedlegg`() {
        val fileContent = SedDokumentfiler(
            SedVedlegg("P2000", MimeType.PDF, "dokInnhold"),
            listOf(SedVedlegg("filnavn", null, "dokInnhold"))
        )
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns fileContent

        val (_, usupportertVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)

        assertEquals(1, usupportertVedlegg.size)
        assertEquals("filnavn", usupportertVedlegg[0].filnavn)
    }

    @Test
    fun `Gitt en json dokument med ugyldig innhold saa blir den satt paa listen over uSupporterte vedlegg`() {
        val fileContent = javaClass.getResource("/pdf/pdfResponseMedUgyldigInnhold.json").readText()
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent, typeRefs())

        val (_, uSupporterteVedlegg) = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)
        assertEquals(1, uSupporterteVedlegg.size)
        assertEquals("UgyldigInnhold.jpg", uSupporterteVedlegg[0].filnavn)
    }

    @Test
    fun `Gitt en json dokument med ugyldig MimeType saa returneres det ugyldige dokumentet i usupporterteVedlegg`() {
        val fileContent =
            javaClass.getResource("/pdf/pdfResponseMedUgyldigMimeType.json").readText()
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(fileContent, typeRefs())

        val docs = pdfService.hentDokumenterOgVedlegg("rinaSakId", "dokumentId", SedType.P2000)
        assertNotNull(docs.second)
        assertNull(docs.second[0].mimeType)
    }
}
