package no.nav.eessi.pensjon.journalforing.services.aktoerregister

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class AktoerregisterServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    lateinit var aktoerregisterService: AktoerregisterService

    @Before
    fun setup() {
        aktoerregisterService = AktoerregisterService(mockrestTemplate)
        aktoerregisterService.appName = "unittests"
    }


    @Test
    fun `hentGjeldendeNorskIdentForAktoerId() should return 1 NorskIdent`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-1-gjeldende-AktoerId.json")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "1000101917358"
        val expectedNorskIdent = "18128126178"

        val response = aktoerregisterService.hentGjeldendeNorskIdentForAkteorId(testAktoerId)
        assertEquals(expectedNorskIdent, response, "AktørId 1000101917358 har norskidenten 18128126178")
    }


    @Test
    fun `hentGjeldendeNorskIdentForAktoerId() should return 1 AktoerId`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-1-gjeldende-NorskIdent.json")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "18128126178"
        val expectedNorskIdent = "1000101917358"

        val response = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(testAktoerId)
        assertEquals(expectedNorskIdent, response, "NorskIdent 18128126178 skal ha AktoerId 100010191735818128126178")
    }


    @Test(expected = AktoerregisterIkkeFunnetException::class)
    fun `hentGjeldendeNorskIdentForAktoerId() should fail if ident is not found in response`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-1-gjeldende-AktoerId.json")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "1234"
        try {
            // the mock returns NorskIdent 18128126178, not 1234 as we asked for
            aktoerregisterService.hentGjeldendeNorskIdentForAkteorId(testAktoerId)
        } catch (rte: RuntimeException) {
            assertTrue(rte.message!!.contains(testAktoerId), "Exception skal si noe om hvilken identen som ikke ble funnet")
            throw rte
        }
    }

    @Test(expected = AktoerregisterIkkeFunnetException::class)
    fun `should throw runtimeexception if no ident is found in response`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/200-OK_0-IdentinfoForAktoer.json")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "18128126178"
        try {
            // the mock returns a valid response, but has no idents
            aktoerregisterService.hentGjeldendeNorskIdentForAkteorId(testAktoerId)
        } catch (rte: RuntimeException) {
            assertTrue(rte.message!!.contains(testAktoerId), "Exception skal si noe om hvilken identen som ikke ble funnet")
            throw rte
        }
    }


    @Test(expected = AktoerregisterException::class)
    fun `AktoerregisterException should be thrown when response contains a 'feilmelding'`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-errormsg.json")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "10000609641830456"
        try {
            // the mock returns a valid response, but with a message in 'feilmelding'
            aktoerregisterService.hentGjeldendeNorskIdentForAkteorId(testAktoerId)
        } catch (are: AktoerregisterException) {
            assertEquals("Den angitte personidenten finnes ikke", are.message!!, "Feilmeldingen fra aktørregisteret skal være exception-message")
            throw are
        }
    }

    @Test(expected = AktoerregisterException::class)
    fun `should throw runtimeexception when multiple idents are returned`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/200-OK_1-IdentinfoForAktoer-with-2-gjeldende-AktoerId.json")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "1000101917358"
        try {
            // the mock returns a valid response, but has 2 gjeldende AktoerId
            aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(testAktoerId)
        } catch (rte: RuntimeException) {
            assertEquals("Forventet 1 ident, fant 2", rte.message!!, "RuntimeException skal kastes dersom mer enn 1 ident returneres")
            throw rte
        }
    }

    @Test(expected = AktoerregisterException::class)
    fun `should throw runtimeexception when 403-forbidden is returned`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("src/test/resources/aktoerregister/403-Forbidden.json", HttpStatus.FORBIDDEN)
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val testAktoerId = "does-not-matter"
        try {
            // the mock returns 403-forbidden
            aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(testAktoerId)
        } catch (rte: RuntimeException) {
            assertEquals("Received 403 Forbidden from aktørregisteret", rte.message!!, "RuntimeException skal kastes dersom mer enn 1 ident returneres")
            throw rte
        }
    }

    private fun createResponseEntityFromJsonFile(filePath: String, httpStatus: HttpStatus = HttpStatus.OK): ResponseEntity<String> {
        val mockResponseString = String(Files.readAllBytes(Paths.get(filePath)))
        return ResponseEntity(mockResponseString, httpStatus)
    }
}