package no.nav.eessi.pensjon.klienter.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals

class OpprettJournalpostModelsTest {

    @Test
    fun `Gitt en gyldig journalpostRequest json når mapping så skal alle felter mappes`() {

        val mapper = jacksonObjectMapper()
        val opprettJournalpostRequestJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostRequest.json")))

        val opprettJournalpostRequest = mapper.readValue(opprettJournalpostRequestJson, OpprettJournalpostRequest::class.java)
        assertEquals(opprettJournalpostRequest.behandlingstema, "ab0254")
        assertEquals(opprettJournalpostRequest.bruker?.id, "12345678912")
        assertEquals(opprettJournalpostRequest.bruker?.idType, "FNR")
        assertEquals(opprettJournalpostRequest.dokumenter, """[{"brevkode":"NAV 14-05.09","dokumentKategori":"SOK","dokumentvarianter":[{"filtype":"PDF/A","fysiskDokument":"string","variantformat":"ARKIV"}],"tittel":"Søknad om foreldrepenger ved fødsel"}]""")
        assertEquals(opprettJournalpostRequest.eksternReferanseId, "string")
        assertEquals(opprettJournalpostRequest.journalfoerendeEnhet, "9999")
        assertEquals(opprettJournalpostRequest.journalpostType, JournalpostType.INNGAAENDE)
        assertEquals(opprettJournalpostRequest.kanal, "NAV_NO")
        assertEquals(opprettJournalpostRequest.sak?.arkivsaksnummer, "string")
        assertEquals(opprettJournalpostRequest.sak?.arkivsaksystem, "PSAK")
        assertEquals(opprettJournalpostRequest.tema, "PEN")
        assertEquals(opprettJournalpostRequest.tittel, "Inngående P2000 - Krav om alderspensjon")
    }

    @Test
    fun `Gitt en gyldig journalpostResponse json når mapping så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val OpprettjournalpostResponseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json")))
        val opprettJournalpostResponse = mapper.readValue(OpprettjournalpostResponseJson, OpprettJournalPostResponse::class.java)
        assertEquals(opprettJournalpostResponse.journalpostId, "429434378")
        assertEquals(opprettJournalpostResponse.journalstatus, "M")
        assertEquals(opprettJournalpostResponse.melding, "null")
    }
}
