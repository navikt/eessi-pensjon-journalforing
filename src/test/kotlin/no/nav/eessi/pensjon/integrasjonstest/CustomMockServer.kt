package no.nav.eessi.pensjon.integrasjonstest

import org.mockserver.client.MockServerClient
import org.mockserver.matchers.Times
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.mockserver.model.StringBody.subString
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
                    .withBody(javaClass.getResource("/pen/bestemSakResponse.json").readText())
                    .withDelay(TimeUnit.SECONDS, 1)
            )
    }

    fun mockBestemSakTom() = apply {

        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(javaClass.getResource("/pen/bestemSakResponseTom.json")!!.readText())
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

    fun mockHentPersonPdl() = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/")
                .withBody(subString("query"))

        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(javaClass.getResource("/pdl/hentPersonResponse.json").readText())
                    .withDelay(TimeUnit.SECONDS, 1)

            )
    }

    fun mockHentPersonPdlGet() = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath("/")
                .withBody(subString("query"))
            ,Times.exactly(1)
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(javaClass.getResource("/pdl/hentPersonResponse.json").readText())
                    .withDelay(TimeUnit.SECONDS, 1)

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
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/arbeidsfordeling")
        )
            .respond(
                withResponse("/norg2/norg2arbeidsfordelig4803result.json")
            )
    }

    fun medEuxGetRequest(bucPath: String, filePath: String) = apply {

        mockServer.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath(bucPath),Times.exactly(1)
        )
            .respond(
                withResponse(filePath)
            )
    }

    fun medEuxGetRequestWithJson(bucPath: String, jsonAsString: String) = apply {
        mockServer.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath(bucPath)
            ,Times.exactly(1)
        ).respond(withResponseAsJsonString(jsonAsString))}

    private fun withResponse(filePath: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(javaClass.getResource(filePath)!!.readText())

    private fun withResponseAsJsonString(jsonString: String) = HttpResponse.response()
        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
        .withStatusCode(HttpStatusCode.OK_200.code())
        .withBody(jsonString)
}