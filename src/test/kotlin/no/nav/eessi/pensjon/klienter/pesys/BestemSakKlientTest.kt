package no.nav.eessi.pensjon.klienter.pesys

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Saktype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.util.*

internal class BestemSakKlientTest {

    private val mockRestTemplate: RestTemplate = mockk(relaxed = true)

    private val bestemSakKlient: BestemSakKlient = BestemSakKlient(mockRestTemplate)

    @BeforeEach
    fun setup() {
        bestemSakKlient.initMetrics()
    }

    @Test
    fun `Verifiser innsendt request ikke endres`() {
        val expectedRequest = BestemSakRequest("12345678901", Saktype.ALDER, UUID.randomUUID(), UUID.randomUUID())
        val responseBody = javaClass.classLoader.getResource("pen/bestemSakResponse.json")!!.readText()

        val requestSlot = slot<HttpEntity<String>>()

        every {
            mockRestTemplate.exchange("/", HttpMethod.POST, capture(requestSlot), String::class.java)
        } returns ResponseEntity.ok(responseBody)

        bestemSakKlient.kallBestemSak(expectedRequest)

        val actualRequest = mapJsonToAny(requestSlot.captured.body!!, typeRefs<BestemSakRequest>())

        assertEquals(expectedRequest, actualRequest)
        verify(exactly = 1) { mockRestTemplate.exchange("/", HttpMethod.POST, any(), String::class.java) }
    }

}
