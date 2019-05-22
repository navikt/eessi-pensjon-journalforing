package no.nav.eessi.pensjon.journalforing.services.eux

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SedDokumenterResponseTest {

    @Test
    fun `Gitt en gyldig sedDokumenterResponse med vedlegg når mapper så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val resp  = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedVedlegg.json"))), SedDokumenterResponse::class.java)

        assertEquals(resp.sed.filnavn, "Sak_163373_dok_3f7bd9d1dfa04af6ac553a734ddc332e.pdf")
        assertEquals(resp.sed.mimeType, MimeType.PDF)
        assertEquals(resp.sed.innhold, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G")

        assertEquals(resp.vedlegg?.size, 2)
        assertEquals(resp.vedlegg?.get(0)?.filnavn, "fintbilde.jpg")
        assertEquals(resp.vedlegg?.get(0)?.mimeType, MimeType.JPG)
        assertEquals(resp.vedlegg?.get(0)?.innhold, "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAIBAQIBAQICAgICAgICAwUDAwMDAwYEBAMFBwYHBwcGBwcICQsJCAgKCAcHCg0")
    }

    @Test
    fun `Gitt en gyldig sedDokumenterResponse uten vedlegg når mapper så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val resp  = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenterResponse::class.java)

        assertEquals(resp.sed.filnavn, "Sak_163373_dok_3f7bd9d1dfa04af6ac553a734ddc332e.pdf")
        assertEquals(resp.sed.mimeType, MimeType.PDF)
        assertEquals(resp.sed.innhold, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G")
        assertNull(resp.vedlegg)
    }
}