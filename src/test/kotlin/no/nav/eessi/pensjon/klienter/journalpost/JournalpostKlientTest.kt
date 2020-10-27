package no.nav.eessi.pensjon.klienter.journalpost

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class JournalpostKlientTest {
    private val mockrestTemplate: RestTemplate = mockk(relaxed = true)

    private lateinit var journalpostKlient: JournalpostKlient

    @BeforeEach
    fun setup() {
        journalpostKlient = JournalpostKlient(mockrestTemplate)
        journalpostKlient.initMetrics()
    }

    @Test
    fun `Sjekker opprettelse av journalpost`(){
        val dummyResponse = javaClass.classLoader.getResource("journalpost/opprettJournalpostResponse.json")!!.readText()

        every { mockrestTemplate.exchange(
                "/journalpost?forsoekFerdigstill=false",
                HttpMethod.POST,
                any(),
                String::class.java)
        } returns ResponseEntity.ok(dummyResponse)

        journalpostKlient.opprettJournalpost(mockk(), false)

        verify(exactly = 1) {
            mockrestTemplate.exchange("/journalpost?forsoekFerdigstill=false", HttpMethod.POST, any(), String::class.java)
        }
    }

    @Test
    fun `Sjekker oppdatering av distribusjonsinfo`(){
        val bodySlot = slot<HttpEntity<String>>()

        val journalPostId = "123"

        every { mockrestTemplate.exchange(
                "/journalpost/$journalPostId/oppdaterDistribusjonsinfo",
                HttpMethod.PATCH,
                capture(bodySlot),
                String::class.java)
        } returns ResponseEntity(HttpStatus.OK)

        journalpostKlient.oppdaterDistribusjonsinfo(journalPostId)

        val actualRequest = mapJsonToAny(bodySlot.captured.body!!, typeRefs<OppdaterDistribusjonsinfoRequest>())

        assertTrue(actualRequest.settStatusEkspedert)
        assertEquals("EESSI", actualRequest.utsendingsKanal)

        verify(exactly = 1) {
            mockrestTemplate.exchange("/journalpost/$journalPostId/oppdaterDistribusjonsinfo", HttpMethod.PATCH, any(), String::class.java)
        }
    }
}
