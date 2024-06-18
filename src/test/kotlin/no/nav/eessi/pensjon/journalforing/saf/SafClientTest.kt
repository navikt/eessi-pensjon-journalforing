package no.nav.eessi.pensjon.journalforing.saf

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import no.nav.eessi.pensjon.journalforing.Journalstatus
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class SafClientTest {

    private val restTemplate: RestTemplate = mockk(relaxed = true)
    private val safClient = SafClient(restTemplate, MetricsHelper.ForTest())

    @Test
    fun `should verify hentJournalpost`() {
        val mockJournalpostId = "222222222"
        val mockJournalpostResponse = journalPostResponseFraFile()
        val mockResponseEntity = ResponseEntity(mockJournalpostResponse, HttpStatus.OK)

        every { restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<String>>(), any<Class<String>>()) } returns mockResponseEntity
        val response = safClient.hentJournalpost(mockJournalpostId)

        assertEquals("11", response?.journalpostId)
        assertEquals("22", response?.bruker?.id)
        assertEquals("0001", response?.journalforendeEnhet)
        assertEquals(Tema.PENSJON, response?.tema)
        assertEquals(Journalstatus.JOURNALFOERT, response?.journalstatus)
        assertEquals(Behandlingstema.ALDERSPENSJON, response?.behandlingstema)
        assertEquals(LocalDateTime.parse("2024-02-12T10:18:26"), response?.datoOpprettet)
    }

    private fun journalPostResponseFraFile(): String {
        return """
            {
              "data": {
                "journalpost": {
                  "journalpostId": "11",
                  "bruker": {
                    "id": "22",
                    "type": "AKTOERID"
                  },
                  "tittel": "Inngående P15000 - Overføring av pensjonssaker til EESSI",
                  "journalposttype": "I",
                  "tema": "PEN",
                  "dokumenter": [
                    {
                      "dokumentInfoId": "454271351",
                      "tittel": "Inngående P15000 - Overføring av pensjonssaker til EESSI",
                      "brevkode": "P15000"
                    }
                  ],
                  "behandlingstema": "ab0254",
                  "journalstatus": "JOURNALFOERT",                 
                  "behandlingstemanavn": "Alderspensjon",
                  "journalforendeEnhet": "0001",
                  "eksternReferanseId": "gdgdgdg-1207-456c-a63e-23sfdssrsd",
                  "tilleggsopplysninger": [
                    {
                      "nokkel": "eessi_pensjon_bucid",
                      "verdi": "45235345345"
                    }
                  ],
                  "datoOpprettet": "2024-02-12T10:18:26"
                }
              }
            }
        """.trimIndent()
    }
}