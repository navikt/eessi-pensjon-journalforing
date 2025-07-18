package no.nav.eessi.pensjon.journalforing.etterlatte

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.*
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

class EtterlatteServiceTest {

    private lateinit var etterlatteRestTemplate: RestTemplate
    private lateinit var etterlatteService: EtterlatteService

    @BeforeEach
    fun setUp() {
        etterlatteRestTemplate = mockk<RestTemplate>()
        etterlatteService = EtterlatteService(etterlatteRestTemplate) // Initialize your class with the mock
    }

    @Test
    fun `hentGjennySak skal gi success naar den finner sak`() {
        val sakId = "12345"
        val responseBody = """{"someKey": "someValue"}"""

        mockSuccessfulSakResponse(sakId, responseBody)

        val result = etterlatteService.hentGjennySak(sakId)

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        verifyRequestSakMadeOnce(sakId)
    }

    @Test
    fun `hentGjennySak skal gi en 404 naar den ikke finner sak`() {
        val sakId = "12345"

        mockNotFoundError(sakId)

        val result = etterlatteService.hentGjennySak(sakId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verifyRequestSakMadeOnce(sakId)
    }

    @Test
    fun `hentGjennySak skal gi en general error naar det ikke er 404`() {
        val sakId = "12345"
        val exceptionMessage = "Some other error"

        mockGeneralError(sakId, exceptionMessage)

        val result = etterlatteService.hentGjennySak(sakId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        verifyRequestSakMadeOnce(sakId)
    }

    @Test
    fun `hentGjennyVedtak skal gi success naar den finner vedtak`() {
        val fnr = "12345678901"
        val responseBody = """
        {
          "vedtak": [
            {
              "sakId": 0,
              "sakType": "string",
              "virkningstidspunkt": "2025-01-01",
              "type": "INNVILGELSE",
              "utbetaling": [
                {
                  "fraOgMed": "2025-01-01",
                  "tilOgMed": "2025-01-31",
                  "beloep": "string"
                }
              ],
              "iverksettelsesTidspunkt": "2025-07-18T14:23:45.123456Z"
            }
          ]
        }
            """.trimMargin()

        mockSuccessfulVedtakResponse(fnr, responseBody)

        val result = etterlatteService.hentGjennyVedtak(fnr)

        assertTrue(result.isSuccess)
        verifyRequestVedtakMadeOnce()
        assertNotNull(result.getOrNull())
        assertEquals(LocalDateTime.parse("2025-07-18T14:23:45.123456"),result.getOrNull()?.vedtak?.firstOrNull()?.iverksettelsesTidspunkt)
    }

    private fun mockSuccessfulSakResponse(sakId: String, responseBody: String) {
        val url = buildSakUrl(sakId)
        val responseEntity = ResponseEntity(responseBody, HttpStatus.OK)

        every {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } returns responseEntity
    }

    private fun mockSuccessfulVedtakResponse(fnr: String, responseBody: String) {
        val url = buildVedtakUrl()
        val responseEntity = ResponseEntity(responseBody, HttpStatus.OK)

        every {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.POST,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } returns responseEntity
    }

    private fun mockNotFoundError(sakId: String) {
        val url = buildSakUrl(sakId)
        every {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } throws HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders(), ByteArray(10), null)
    }

    private fun mockGeneralError(sakId: String, exceptionMessage: String) {
        val url = buildSakUrl(sakId)
        every {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } throws RuntimeException(exceptionMessage)
    }

    private fun buildSakUrl(sakId: String): String {
        return "/api/sak/$sakId"
    }

    private fun buildVedtakUrl(): String {
        return "/api/v1/vedtak"
    }

    private fun verifyRequestSakMadeOnce(sakId: String) {
        val url = buildSakUrl(sakId)
        verify(exactly = 1) {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        }
    }

    private fun verifyRequestVedtakMadeOnce() {
        val url = buildVedtakUrl()
        verify(exactly = 1) {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.POST,
                any<HttpEntity<String>>(),
                String::class.java
            )
        }
    }
}