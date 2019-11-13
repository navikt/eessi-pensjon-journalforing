package no.nav.eessi.pensjon.services.eux

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.doThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import sun.net.www.http.HttpClient

@ExtendWith(MockitoExtension::class)
class EuxServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    lateinit var euxService: EuxService

    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)


    @BeforeEach
    fun setup() {
        euxService = EuxService(mockrestTemplate)
    }

    @Test
    fun `Gitt gyldig request når etterspør pdf for SED så motta base64 encodet pdf`() {
        val rinaNr = "123"
        val dokumentId = "456"
        doReturn(
                ResponseEntity(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))), HttpStatus.OK))
                .`when`(mockrestTemplate).exchange(
                    eq("/buc/$rinaNr/sed/$dokumentId/filer"),
                    any(HttpMethod::class.java),
                    any(HttpEntity::class.java),
                    eq(String::class.java))

        val resp = euxService.hentSedDokumenter(rinaNr, dokumentId)
        val innhold = mapper.readValue(resp, JsonNode::class.java).path("sed").path("innhold").textValue()
        assertEquals("JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G", innhold)
    }

    @Test
    fun `Gitt ugyldig request når etterspør pdf for SED så kast exception`() {
        val rinaNr = "-1"
        val dokumentId = "-1"
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND, "bla bla bla finnes ikke"))
                .`when`(mockrestTemplate).exchange(
                        eq("/buc/$rinaNr/sed/$dokumentId/filer"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        assertThrows<RuntimeException> {
            euxService.hentSedDokumenter(rinaNr, dokumentId)
        }
    }

    @Test
    fun `Gitt gyldig request når etterspør fødselsdato for SED så motta fødselsdato`() {
        val rinaNr = "123"
        val dokumentId = "456"
        doReturn(
                ResponseEntity(String(Files.readAllBytes(Paths.get("src/test/resources/eux/SedResponseP2000.json"))), HttpStatus.OK))
                .`when`(mockrestTemplate).exchange(
                        eq("/buc/$rinaNr/sed/$dokumentId"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))


        val resp = euxService.hentFodselsDatoFraSed(rinaNr, dokumentId)
        assertEquals("1980-01-01", resp)
    }
}
