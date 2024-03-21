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

    var mockServer = MockServerClient("localhost", System.getProperty("mockServerport").toInt())

    fun medOppdaterDistribusjonsinfo() = apply {
        // Mocker oppdaterDistribusjonsinfo
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("PATCH")
                .withPath("/journalpost/.*/oppdaterDistribusjonsinfo")
        )
        .respond(
            HttpResponse.response()
                .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withBody("")
        )
    }

    fun medStatusAvbrutt() = apply {
        // Mocker sett status avbrutt
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("PATCH")
                .withPath("/journalpost/.*/settStatusAvbryt")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody("")
            )
    }


    fun mockBestemSak() = apply {

        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/")
        )
        .respond(
            HttpResponse.response()
                .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withBody(javaClass.getResource("/pen/bestemSakGjenlevendeResponse.json").readText())
                .withDelay(TimeUnit.SECONDS, 1)
        )
    }

    fun mockPensjonsinformasjon() = apply {

        mockServer.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath("/pensjon/sakliste/.*")
        )
        .respond(
            HttpResponse.response()
                .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withBody(javaClass.getResource("/pen/pensjonsinformasjonResponse.json")!!.readText())
                .withDelay(TimeUnit.SECONDS, 1)
        )
    }

    fun mockHttpRequestFromFileWithBodyContains(bodyContains: String, httpMethod: HttpMethod, response: String) = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod(httpMethod.name())
                .withBody(subString(bodyContains))
        )
        .respond(
            withResponseFromFile(response)
        )
    }
    fun mockHttpRequestFromJsonWithBodyContains(bodyContains: String, httpMethod: HttpMethod, response: String) = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod(httpMethod.name())
                .withBody(subString(bodyContains))
        )
        .respond(
            withResponseAsJsonString(response)
        )
    }

    fun mockLagreJournalPostDetaljer() {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/journalpost/.*")
        )

    }

    /**
     * mocker journalføringresponse
     * @param {Boolean} forsoekFerdigstill, gjør det mulig å benytte krav init
     * @param {String} journalpostId, erstatter default id
     * */
    fun medJournalforing(forsoekFerdigstill: Boolean = false, journalpostId: String = "429434378") = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/journalpost")
        )
        .respond(
            when (forsoekFerdigstill) {
                true -> {
                    val reponse = withResponseFromFile("/journalpost/opprettJournalpostResponseTrue.json")
                    reponse.withBody(reponse.body.toString().replace("429434378", journalpostId))
                }
                false -> {
                    val reponse = withResponseFromFile("/journalpost/opprettJournalpostResponseFalse.json")
                    reponse.withBody(reponse.body.toString().replace("429434378", journalpostId))
                }
            }
        )
    }

    fun medNorg2Tjeneste() = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/arbeidsfordeling")
        )
        .respond(
            withResponseFromFile("/norg2/norg2arbeidsfordelig4803result.json")
        )
    }

    fun mockHttpRequestWithResponseFromFile(path: String, httpMethod: HttpMethod, response: String) = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod(httpMethod.name())
                .withPath(path)

        ).respond(withResponseFromFile(response))}

    fun mockHttpRequestWithResponseFromJson(path: String, httpMethod: HttpMethod, responseAsJson: String) = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod(httpMethod.name())
                .withPath(path)

        ).respond(withResponseAsJsonString(responseAsJson))}

    private fun withResponseFromFile(filePath: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(javaClass.getResource(filePath)!!.readText())

    private fun withResponseAsJsonString(jsonString: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(jsonString)
}