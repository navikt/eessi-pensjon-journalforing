package no.nav.eessi.pensjon.pdf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class SedDokumenterTest {

    @Test
    fun `Gitt en gyldig sedDokumenter med vedlegg når mapper så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val resp  = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseMedVedlegg.json"))), SedDokumenter::class.java)

        assertEquals(resp.sed.filnavn, "Sak_163373_dok_3f7bd9d1dfa04af6ac553a734ddc332e.pdf")
        assertEquals(resp.sed.mimeType, MimeType.PDF)
        assertEquals(resp.sed.innhold, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G")

        assertEquals(resp.vedlegg?.size, 2)
        assertEquals(resp.vedlegg?.get(0)?.filnavn, "jpg.jpg")
        assertEquals(resp.vedlegg?.get(0)?.mimeType, MimeType.JPG)
        assertEquals(resp.vedlegg?.get(0)?.innhold, "/9j/4AAQSkZJRgABAQEASABIAAD//gATQ3JlYXRlZCB3aXRoIEdJTVD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wgARCAAFAAUDAREAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQBAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhADEAAAAVSf/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABCf/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPxB//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPxB//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxB//9k=")
    }

    @Test
    fun `Gitt en gyldig sedDokumenter uten vedlegg når mapper så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val resp  = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), SedDokumenter::class.java)

        assertEquals(resp.sed.filnavn, "Sak_163373_dok_3f7bd9d1dfa04af6ac553a734ddc332e.pdf")
        assertEquals(resp.sed.mimeType, MimeType.PDF)
        assertEquals(resp.sed.innhold, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G")
        assertNull(resp.vedlegg)
    }
}
