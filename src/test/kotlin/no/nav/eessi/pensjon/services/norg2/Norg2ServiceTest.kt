package no.nav.eessi.pensjon.services.norg2

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class Norg2ServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    private lateinit var norg2Service: Norg2Service

    @BeforeEach
    fun setup() {
        norg2Service = Norg2Service(mockrestTemplate)
    }

    @Test
    fun `finn fordeligsenhet for utland`() {
        val enheter =  mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig0001result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0107"
        )

        val expected = "0001"
        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for utland feiler`() {
        val enheter = listOf<Norg2ArbeidsfordelingItem>()

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0107"
        )

        val expected = null
        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for Oslo`() {
        val enheter =  mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4803result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0104"
        )

        val expected = "4803"
        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for Aalesund`() {
        val enheter =  mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4862result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0104"
        )

        val expected = "4862"
        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Norg2 avd Oslo`() {
        val response = ResponseEntity.ok().body(getJsonFileFromResource("norg2arbeidsfordelig4803result.json"))
        doReturn(response)
                .whenever(mockrestTemplate).exchange(
                        contains("/api/v1/arbeidsfordeling"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java)
                )

        val request = norg2Service.opprettNorg2ArbeidsfordelingRequest("NOR", "0422",null)

        val result = norg2Service.hentArbeidsfordelingEnheter(request)
        assertEquals(4, result?.size)

        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = result)
        assertEquals("4803", actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Utland`() {
        val response = ResponseEntity.ok().body(getJsonFileFromResource("norg2arbeidsfordelig0001result.json"))
        doReturn(response)
                .whenever(mockrestTemplate).exchange(
                        contains("/api/v1/arbeidsfordeling"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java)
                )

        val request = norg2Service.opprettNorg2ArbeidsfordelingRequest(null, null,null)

        val result = norg2Service.hentArbeidsfordelingEnheter(request)
        assertEquals(2, result?.size)

        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = result)
        assertEquals("0001", actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter ved diskresjon`() {
        val response = ResponseEntity.ok().body(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"))
        doReturn(response)
                .whenever(mockrestTemplate).exchange(
                        contains("/api/v1/arbeidsfordeling"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java)
                )

        val request = norg2Service.opprettNorg2ArbeidsfordelingRequest("NOR", null, Diskresjonskode.SPSF)

        val result = norg2Service.hentArbeidsfordelingEnheter(request)
        assertEquals(3, result?.size)

        val actual = norg2Service.finnKorrektArbeidsfordelingEnheter(request, list = result)
        assertEquals("2103", actual)
    }

    fun getJsonFileFromResource(filename: String): String {
        return String(Files.readAllBytes(Paths.get("src/test/resources/norg2/$filename")))
    }
}

