package no.nav.eessi.pensjon.klienter.journalpost

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.journalforing.OppdaterDistribusjonsinfoRequest
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.*
import org.springframework.web.client.RestTemplate

internal class JournalpostKlientTest {
    private val mockrestTemplate: RestTemplate = mockk(relaxed = true)

    private lateinit var journalpostKlient: JournalpostKlient

    @BeforeEach
    fun setup() {
        journalpostKlient = JournalpostKlient(mockrestTemplate)
    }

    @Test
    fun `Sjekker opprettelse av journalpost`(){
        val dummyResponse = javaClass.classLoader.getResource("journalpost/opprettJournalpostResponseFalse.json")!!.readText()
        val opprettJournalpostRequestJson = javaClass.getResource("/journalpost/opprettJournalpostRequest.json")!!.readText()
        val opprettJournalpostRequest = mapJsonToAny<OpprettJournalpostRequest>((opprettJournalpostRequestJson))

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        every { mockrestTemplate.exchange(
                "/journalpost?forsoekFerdigstill=false",
                HttpMethod.POST,
                HttpEntity(opprettJournalpostRequest.toString(), headers),
                String::class.java)
        } returns ResponseEntity.ok(dummyResponse)

        journalpostKlient.opprettJournalpost(opprettJournalpostRequest, false, null)

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

        val actualRequest = mapJsonToAny<OppdaterDistribusjonsinfoRequest>(bodySlot.captured.body!!)

        assertTrue(actualRequest.settStatusEkspedert)
        assertEquals("EESSI", actualRequest.utsendingsKanal)

        verify(exactly = 1) {
            mockrestTemplate.exchange("/journalpost/$journalPostId/oppdaterDistribusjonsinfo", HttpMethod.PATCH, any(), String::class.java)
        }
    }
}
