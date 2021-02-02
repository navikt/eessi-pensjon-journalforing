package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.klienter.norg2.Norg2ArbeidsfordelingRequest
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

internal class Norg2KlientTest {

    private val mockrestTemplate = mockk<RestTemplate>()

    private val norg2Klient = Norg2Klient(mockrestTemplate)

    @BeforeEach
    fun setup() {
        norg2Klient.initMetrics()
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Norg2 avd Oslo`() {
        val response = getJsonFileFromResource("/norg2/norg2arbeidsfordelig4803result.json")

        every {
            mockrestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    any(),
                    String::class.java
            )
        } returns ResponseEntity.ok(response)

        val result = norg2Klient.hentArbeidsfordelingEnheter(Norg2ArbeidsfordelingRequest())
        assertEquals(4, result.size)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Utland`() {
        val response = getJsonFileFromResource("/norg2/norg2arbeidsfordelig0001result.json")

        every {
            mockrestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    any(),
                    String::class.java
            )
        } returns ResponseEntity.ok(response)

        val result = norg2Klient.hentArbeidsfordelingEnheter(Norg2ArbeidsfordelingRequest())
        assertEquals(2, result.size)
    }

    @Test
    fun `hent arbeidsfordeligEnheter ved diskresjon`() {
        val response = getJsonFileFromResource("/norg2/norg2arbeidsfordeling2103result.json")
        every {
            mockrestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    any(),
                    String::class.java
            )
        } returns ResponseEntity.ok(response)

        val result = norg2Klient.hentArbeidsfordelingEnheter(Norg2ArbeidsfordelingRequest())
        assertEquals(3, result.size)
    }

    private fun getJsonFileFromResource(filename: String): String =
        javaClass.getResource(filename).readText()
}
