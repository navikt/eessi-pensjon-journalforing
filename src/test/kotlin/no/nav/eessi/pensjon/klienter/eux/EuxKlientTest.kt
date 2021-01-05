package no.nav.eessi.pensjon.klienter.eux

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull

internal class EuxKlientTest {

    private val mockrestTemplate = mockk<RestTemplate>()

    private val euxKlient = EuxKlient(mockrestTemplate)

    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

    @BeforeEach
    fun setup() {
        euxKlient.initMetrics()
    }

    @Test
    fun `Gitt gyldig request når etterspør pdf for SED så motta base64 encodet pdf`() {
        val rinaNr = "123"
        val dokumentId = "456"

        every {
            mockrestTemplate.exchange(
                    "/buc/$rinaNr/sed/$dokumentId/filer",
                    HttpMethod.GET,
                    any(),
                    String::class.java
            )
        } returns ResponseEntity.ok(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json"))))

        val resp = euxKlient.hentSedDokumenter(rinaNr, dokumentId)
        val innhold = mapper.readValue(resp, JsonNode::class.java).path("sed").path("innhold").textValue()
        assertEquals("JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G", innhold)
    }

    @Test
    fun `Gitt ugyldig request når etterspør pdf for SED så kast exception`() {
        val rinaNr = "-1"
        val dokumentId = "-1"

        every {
            mockrestTemplate.exchange(
                    "/buc/$rinaNr/sed/$dokumentId/filer",
                    HttpMethod.GET,
                    any(),
                    String::class.java
            )
        } throws HttpClientErrorException(HttpStatus.NOT_FOUND, "bla bla bla finnes ikke")

        assertThrows<RuntimeException> {
            euxKlient.hentSedDokumenter(rinaNr, dokumentId)
        }
    }

    @Test
    fun `Gitt gyldig request ved uthenting av SED`() {
        val rinaNr = "123"
        val dokumentId = "456"

        every {
            mockrestTemplate.getForObject("/buc/$rinaNr/sed/$dokumentId", SED::class.java)
        } returns SED(SedType.P8000)

        val resp = euxKlient.hentSed(rinaNr, dokumentId)

        assertNotNull(resp)
    }

    @Test
    fun `Gitt ugyldig request ved uthenting av SED, skal kaste exception`() {
        val rinaNr = "-1"
        val dokumentId = "-1"

        every {
            mockrestTemplate.getForObject("/buc/$rinaNr/sed/$dokumentId", SED::class.java)
        } throws HttpClientErrorException(HttpStatus.NOT_FOUND, "bla bla bla finnes ikke")

        assertThrows<RuntimeException> {
            euxKlient.hentSedDokumenter(rinaNr, dokumentId)
        }
    }
}
