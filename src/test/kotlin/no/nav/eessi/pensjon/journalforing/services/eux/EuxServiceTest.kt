package no.nav.eessi.pensjon.journalforing.services.eux

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class EuxServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    lateinit var euxService: EuxService

    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)


    @Before
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

    @Test( expected = RuntimeException::class)
    fun `Gitt ugyldig request når etterspør pdf for SED så kast exception`() {
        val rinaNr = "-1"
        val dokumentId = "-1"
        doReturn(
                ResponseEntity("", HttpStatus.INTERNAL_SERVER_ERROR))
                .`when`(mockrestTemplate).exchange(
                        eq("/buc/$rinaNr/sed/$dokumentId/filer"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

            euxService.hentSedDokumenter(rinaNr, dokumentId)
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


        val resp = euxService.hentFodselsDato(rinaNr, dokumentId)
        assertEquals("1980-01-01", resp)
    }
}