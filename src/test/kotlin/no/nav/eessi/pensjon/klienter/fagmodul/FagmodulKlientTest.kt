package no.nav.eessi.pensjon.klienter.fagmodul

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `Verify exchange and serde works as intented`() {
        every {
            mockRestTemplate.exchange("/buc/1/allDocuments", HttpMethod.GET, null, String::class.java)
        } returns ResponseEntity.ok(javaClass.getResource("/fagmodul/alldocumentsids.json").readText())

        val docs = klient.hentAlleDokumenter("1")
        assertEquals(3, docs.size)

        docs.forEach {
            assertNotNull(it.id)
            assertNotNull(it.status)
            assertNotNull(it.type)
        }

        verify(exactly = 1) { mockRestTemplate.exchange("/buc/1/allDocuments", HttpMethod.GET, null, String::class.java) }
    }
}