package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths

internal class Norg2KlientTest {

    private val mockrestTemplate = mockk<RestTemplate>()

    private val norg2Klient = Norg2Klient(mockrestTemplate)

    @BeforeEach
    fun setup() {
        norg2Klient.initMetrics()
    }

    @Test
    fun `finn fordeligsenhet for utland`() {
        val enheter =  mapJsonToAny(getJsonFileFromResource("/norg2/norg2arbeidsfordelig0001result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0107"
        )

        val expected = "0001"
        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for utland feiler`() {
        val enheter = listOf<Norg2ArbeidsfordelingItem>()

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0107"
        )

        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = enheter)

        assertNull(actual)
    }

    @Test
    fun `finn fordeligsenhet for Oslo`() {
        val enheter =  mapJsonToAny(getJsonFileFromResource("/norg2/norg2arbeidsfordelig4803result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0104"
        )

        val expected = "4803"
        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for Aalesund`() {
        val enheter =  mapJsonToAny(getJsonFileFromResource("/norg2/norg2arbeidsfordelig4862result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0104"
        )

        val expected = "4862"
        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Norg2 avd Oslo`() {
        val response = ResponseEntity.ok().body(getJsonFileFromResource("/norg2/norg2arbeidsfordelig4803result.json"))
        every {
            mockrestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    any(),
                    String::class.java
            )
        } returns response

        val request = norg2Klient.opprettNorg2ArbeidsfordelingRequest(NorgKlientRequest(
                landkode = "NOR",
                geografiskTilknytning = "0422"))

        val result = norg2Klient.hentArbeidsfordelingEnheter(request)
        assertEquals(4, result.size)

        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = result)
        assertEquals("4803", actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Utland`() {
        val response = ResponseEntity.ok().body(getJsonFileFromResource("/norg2/norg2arbeidsfordelig0001result.json"))
        every {
            mockrestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    any(),
                    String::class.java
            )
        } returns response

        val request = norg2Klient.opprettNorg2ArbeidsfordelingRequest(NorgKlientRequest())

        val result = norg2Klient.hentArbeidsfordelingEnheter(request)
        assertEquals(2, result.size)

        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = result)
        assertEquals("0001", actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter ved diskresjon`() {
        val response = ResponseEntity.ok().body(getJsonFileFromResource("/norg2/norg2arbeidsfordeling2103result.json"))
        every {
            mockrestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    any(),
                    String::class.java
            )
        } returns response

        val request = norg2Klient.opprettNorg2ArbeidsfordelingRequest(NorgKlientRequest(
                landkode = "NOR",
                harAdressebeskyttelse = true))

        val result = norg2Klient.hentArbeidsfordelingEnheter(request)
        assertEquals(3, result.size)

        val actual = norg2Klient.finnArbeidsfordelingEnheter(request, list = result)
        assertEquals("2103", actual)
    }

    private fun getJsonFileFromResource(filename: String): String =
        javaClass.getResource(filename).readText()
}
