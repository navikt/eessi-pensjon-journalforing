package no.nav.eessi.pensjon.integrasjonstest

import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.springframework.http.HttpMethod
import java.util.concurrent.*

class CustomMockServer(
) {
    val serverPort = CompletableFuture.completedFuture(System.getProperty("mockServerport").toInt()).get().toInt()

    fun medOppdaterDistribusjonsinfo() = apply {
        // Mocker oppdaterDistribusjonsinfo
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.PATCH.name)
                .withPath("/journalpost/.*/oppdaterDistribusjonsinfo")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody("")
            )
    }

    fun mockBestemSak() = apply {

        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(javaClass.getResource("/pen/bestemSakResponse.json").readText())
                    .withDelay(TimeUnit.SECONDS, 1)
            )
    }

    /**
     * mocker journalføringresponse
     * @param {Boolean} forsoekFerdigstill, gjør det mulig å benytte krav init
     * @param {String} journalpostId, erstatter default id
     * */
    fun medJournalforing(forsoekFerdigstill: Boolean = false, journalpostId: String = "429434378") = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/journalpost")
        )
            .respond(
                when (forsoekFerdigstill) {
                    true -> {
                        val reponse = withResponse("/journalpost/opprettJournalpostResponseTrue.json")
                        reponse.withBody(reponse.body.toString().replace("429434378", journalpostId))
                    }
                    false -> {
                        val reponse = withResponse("/journalpost/opprettJournalpostResponseFalse.json")
                        reponse.withBody(reponse.body.toString().replace("429434378", journalpostId))
                    }
                }
            )
    }

    fun medNorg2Tjeneste() = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/api/v1/arbeidsfordeling")
        )
            .respond(
                withResponse("/norg2/norg2arbeidsfordelig4803result.json")
            )
    }

    fun medEuxGetRequest(bucPath: String, filePath: String) = apply {

        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(bucPath)
        )
            .respond(
                withResponse(filePath)
            )
    }

    fun medEuxGetRequestWithJson(bucPath: String, jsonAsString: String) = apply {

        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(bucPath)
        )
            .respond(
                withResponseAsJsonString(jsonAsString)
            )
    }

    private fun withResponse(filePath: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(javaClass.getResource(filePath).readText())

    private fun withResponseAsJsonString(jsonString: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(jsonString)
}