package no.nav.eessi.pensjon.integrasjonstest

import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.mockserver.model.StringBody.subString
import org.springframework.http.HttpMethod
import java.util.concurrent.TimeUnit

class CustomMockServer {


    private val mockServer = MockServerClient("localhost", System.getProperty("mockServerport").toInt())

    fun medOppdaterDistribusjonsinfo() = apply {
        mockPatchRequest("/journalpost/.*/oppdaterDistribusjonsinfo", "")
    }

    fun medStatusAvbrutt() = apply {
        mockPatchRequest("/journalpost/.*/settStatusAvbryt", "")
    }

    fun mockBestemSak() = apply {
        mockPostRequest("/", "/pen/bestemSakGjenlevendeResponse.json", delaySeconds = 1)
    }

    fun mockPensjonsinformasjon() = apply {
        mockGetRequest("/pensjon/sakliste/.*", "/pen/pensjonsinformasjonResponse.json", delaySeconds = 1)
    }

    fun mockHttpRequestFromFileWithBodyContains(bodyContains: String, httpMethod: HttpMethod, response: String) = apply {
        mockRequestWithBodyContains(bodyContains, httpMethod, withResponseFromFile(response))
    }

    fun mockHttpRequestFromJsonWithBodyContains(bodyContains: String, httpMethod: HttpMethod, response: String) = apply {
        mockRequestWithBodyContains(bodyContains, httpMethod, withResponseAsJsonString(response))
    }

    fun medJournalforing(forsoekFerdigstill: Boolean = false, journalpostId: String = "429434378") = apply {
        val responseFile = if (forsoekFerdigstill) "/journalpost/opprettJournalpostResponseTrue.json"
        else "/journalpost/opprettJournalpostResponseFalse.json"
        mockPostRequest("/journalpost", responseFile, journalpostId)
    }

    fun medJournalforingMedUkjentBruker(forsoekFerdigstill: Boolean = false, journalpostId: String = "429434378") = apply {
        val responseFile = if (forsoekFerdigstill) "/journalpost/opprettJournalpostResponseTrue.json"
        else "/journalpost/opprettJournalpostResponseFalse.json"
        mockPatchRequest("/journalpost/$journalpostId/feilregistrer/settUkjentBruker", responseFile, journalpostId)
    }

    fun medNorg2Tjeneste() = apply {
        mockPostRequest("/api/v1/arbeidsfordeling", "/norg2/norg2arbeidsfordelig4803result.json")
    }

    fun medGjennyResponse() = apply {
        mockGetRequest("/api/sak/123123124341345134513513451345134513513451345", "", delaySeconds = 1)
    }

    fun mockHttpRequestWithResponseFromFile(path: String, httpMethod: HttpMethod, response: String) = apply {
        mockRequest(path, httpMethod, withResponseFromFile(response))
    }

    fun mockHttpRequestWithResponseFromJson(path: String, httpMethod: HttpMethod, responseAsJson: String) = apply {
        mockRequest(path, httpMethod, withResponseAsJsonString(responseAsJson))
    }

    private fun mockRequest(path: String, httpMethod: HttpMethod, response: HttpResponse) {
        mockServer.`when`(HttpRequest.request().withMethod(httpMethod.name()).withPath(path)).respond(response)
    }

    private fun mockRequestWithBodyContains(bodyContains: String, httpMethod: HttpMethod, response: HttpResponse) {
        mockServer.`when`(
            HttpRequest.request().withMethod(httpMethod.name()).withBody(subString(bodyContains))
        ).respond(response)
    }

    private fun mockPostRequest(path: String, responseFile: String, journalpostId: String? = null, delaySeconds: Long = 0) {
        val response = withResponseFromFile(responseFile).applyJournalpostId(journalpostId).applyDelay(delaySeconds)
        mockRequest(path, HttpMethod.POST, response)
    }

    private fun mockPatchRequest(path: String, responseFile: String, journalpostId: String? = null, delaySeconds: Long = 0) {
        val response = withResponseFromFile(responseFile).applyJournalpostId(journalpostId).applyDelay(delaySeconds)
        mockRequest(path, HttpMethod.PATCH, response)
    }

    private fun mockGetRequest(path: String, responseFile: String, delaySeconds: Long = 0) {
        val response = withResponseFromFile(responseFile).applyDelay(delaySeconds)
        mockRequest(path, HttpMethod.GET, response)
    }

    private fun withResponseFromFile(filePath: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(javaClass.getResource(filePath)!!.readText())

    private fun withResponseAsJsonString(jsonString: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(jsonString)

    private fun HttpResponse.applyJournalpostId(journalpostId: String?) = apply {
        if (journalpostId != null) {
            withBody(body.toString().replace("429434378", journalpostId))
        }
    }

    private fun HttpResponse.applyDelay(seconds: Long) = apply {
        if (seconds > 0) withDelay(TimeUnit.SECONDS, seconds)
    }
}