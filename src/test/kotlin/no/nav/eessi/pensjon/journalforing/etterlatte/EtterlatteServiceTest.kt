package no.nav.eessi.pensjon.journalforing.etterlatte

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.*
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

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

        mockSuccessfulResponse(sakId, responseBody)

        val result = etterlatteService.hentGjennySak(sakId)

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        verifyRequestMadeOnce(sakId)
    }

    @Test
    fun `hentGjennySak skal gi en 404 naar den ikke finner sak`() {
        val sakId = "12345"

        mockNotFoundError(sakId)

        val result = etterlatteService.hentGjennySak(sakId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verifyRequestMadeOnce(sakId)
    }

    @Test
    fun `hentGjennySak skal gi en general error naar det ikke er 404`() {
        val sakId = "12345"
        val exceptionMessage = "Some other error"

        mockGeneralError(sakId, exceptionMessage)

        val result = etterlatteService.hentGjennySak(sakId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        verifyRequestMadeOnce(sakId)
    }

    private fun mockSuccessfulResponse(sakId: String, responseBody: String) {
        val url = buildUrl(sakId)
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

    private fun mockNotFoundError(sakId: String) {
        val url = buildUrl(sakId)
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
        val url = buildUrl(sakId)
        every {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } throws RuntimeException(exceptionMessage)
    }

    private fun buildUrl(sakId: String): String {
        return "/api/sak/$sakId"
    }

    private fun verifyRequestMadeOnce(sakId: String) {
        val url = buildUrl(sakId)
        verify(exactly = 1) {
            etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        }
    }
}