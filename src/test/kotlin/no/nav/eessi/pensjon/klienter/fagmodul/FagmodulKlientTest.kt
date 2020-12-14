package no.nav.eessi.pensjon.klienter.fagmodul

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class FagmodulKlientTest {

    private val mockRestTemplate = mockk<RestTemplate>()

    private val klient = FagmodulKlient(mockRestTemplate)

    @BeforeAll
    fun beforeAll() {
        klient.initMetrics()
    }

    @Test
    fun asdf() {
        every {
            mockRestTemplate.exchange("/buc/1/allDocuments", HttpMethod.GET, null, String::class.java)
        } returns ResponseEntity.ok(javaClass.getResource("/fagmodul/alldocumentsids.json").readText())

        val docs = klient.hentAlleDokumenter2("1")

        println()
    }
}