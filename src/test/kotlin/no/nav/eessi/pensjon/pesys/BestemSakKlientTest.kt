package no.nav.eessi.pensjon.pesys

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.listeners.pesys.BestemSakKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakRequest
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.util.*

internal class BestemSakKlientTest {

    private val mockRestTemplate: RestTemplate = mockk(relaxed = true)

    private val bestemSakKlient: BestemSakKlient = BestemSakKlient(mockRestTemplate)

    @Test
    fun `Verifiser innsendt request ikke endres`() {
        val expectedRequest = BestemSakRequest("12345678901", ALDER, UUID.randomUUID(), UUID.randomUUID())
        val responseBody = javaClass.classLoader.getResource("pen/bestemSakResponse.json")!!.readText()

        val requestSlot = slot<HttpEntity<String>>()

        every {
            mockRestTemplate.exchange("/", HttpMethod.POST, capture(requestSlot), String::class.java)
        } returns ResponseEntity.ok(responseBody)

        bestemSakKlient.kallBestemSak(expectedRequest)

        val actualRequest = mapJsonToAny<BestemSakRequest>(requestSlot.captured.body!!)

        assertEquals(expectedRequest, actualRequest)
        verify(exactly = 1) { mockRestTemplate.exchange("/", HttpMethod.POST, any(), String::class.java) }
    }

}
