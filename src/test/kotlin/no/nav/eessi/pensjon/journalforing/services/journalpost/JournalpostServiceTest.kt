package no.nav.eessi.pensjon.journalforing.services.journalpost

import no.nav.eessi.pensjon.journalforing.models.HendelseType
import org.mockito.Mockito.doReturn
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import org.springframework.http.*

@RunWith(MockitoJUnitRunner::class)
class JournalpostServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    private lateinit var journalpostService: JournalpostService

    @Before
    fun setup() {
        journalpostService = JournalpostService(mockrestTemplate)
    }

    @Test
    fun `Gitt gyldig argumenter så sender request med riktig body og url parameter`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json")))

        doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        anyString(),
                        any(),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        val journalpotReponse = journalpostService.opprettJournalpost(
                journalpostRequest = JournalpostRequest.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json")))),
                sedHendelseType = HendelseType.SENDT,
                forsokFerdigstill = false)
        assertEquals(journalpotReponse.journalpostId, "429434378")
        assertEquals(journalpotReponse.journalstatus, "M")
        assertEquals(journalpotReponse.melding, "null")
    }

    @Test( expected = RuntimeException::class)
    fun `Gitt ugyldig request når forsøker oprette journalpost så kast exception`() {
        doReturn(
                ResponseEntity(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json"))), HttpStatus.INTERNAL_SERVER_ERROR))
                .`when`(mockrestTemplate).exchange(
                        eq("/journalpost?forsoekFerdigstill=false"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        journalpostService.opprettJournalpost(
                journalpostRequest = JournalpostRequest.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json")))),
                sedHendelseType = HendelseType.SENDT,
                forsokFerdigstill = false)
    }
}