package no.nav.eessi.pensjon.klienter.journalpost

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Tema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class OpprettJournalpostModelTest {
    @Test
    fun `Test at serialisering og deserialisering fungerer som forventet`() {
        val actualRequest = OpprettJournalpostRequest(
                AvsenderMottaker(land = "GB"),
                Behandlingstema.ALDERSPENSJON,
                Bruker("brukerId"),
                "[]",
                Enhet.ID_OG_FORDELING,
                JournalpostType.INNGAAENDE,
                Sak("arkivsaksnummer123", "arksys2"),
                Tema.PENSJON,
                emptyList(),
                "tittel på sak")

        val serialized = actualRequest.toJson()

        assertTrue(serialized.contains("\"behandlingstema\" : \"ab0254\""))
        assertTrue(serialized.contains("\"journalfoerendeEnhet\" : \"4303\""))
        assertTrue(serialized.contains("\"journalpostType\" : \"INNGAAENDE\""))
        assertTrue(serialized.contains("\"tema\" : \"PEN\""))

        val deserialized = mapJsonToAny(serialized, typeRefs<OpprettJournalpostRequest>())

        assertEquals(actualRequest.avsenderMottaker, deserialized.avsenderMottaker)
        assertEquals(actualRequest.behandlingstema, deserialized.behandlingstema)
        assertEquals(actualRequest.bruker, deserialized.bruker)
        assertEquals(actualRequest.dokumenter, deserialized.dokumenter)
        assertEquals(actualRequest.eksternReferanseId, deserialized.eksternReferanseId)
        assertEquals(actualRequest.journalfoerendeEnhet, deserialized.journalfoerendeEnhet)
        assertEquals(actualRequest.journalpostType, deserialized.journalpostType)
        assertEquals(actualRequest.kanal, deserialized.kanal)
        assertEquals(actualRequest.sak, deserialized.sak)
        assertEquals(actualRequest.tema, deserialized.tema)
        assertEquals(actualRequest.tilleggsopplysninger, deserialized.tilleggsopplysninger)
        assertEquals(actualRequest.tittel, deserialized.tittel)
    }

    @Test
    fun `Gitt en gyldig journalpostRequest json når mapping så skal alle felter mappes`() {

        val opprettJournalpostRequestJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostRequest.json")))

        val opprettJournalpostRequest = mapJsonToAny(opprettJournalpostRequestJson, typeRefs<OpprettJournalpostRequest>())
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequest.behandlingstema)
        assertEquals("12345678912", opprettJournalpostRequest.bruker?.id)
        assertEquals("FNR", opprettJournalpostRequest.bruker?.idType)
        val dokumenter = """[{"brevkode":"NAV 14-05.09","dokumentKategori":"SOK","dokumentvarianter":[{"filtype":"PDF/A","fysiskDokument":"string","variantformat":"ARKIV"}],"tittel":"Søknad om foreldrepenger ved fødsel"}]"""
        assertEquals(dokumenter, opprettJournalpostRequest.dokumenter)
        assertEquals("string", opprettJournalpostRequest.eksternReferanseId)
        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, opprettJournalpostRequest.journalfoerendeEnhet)
        assertEquals(JournalpostType.INNGAAENDE, opprettJournalpostRequest.journalpostType)
        assertEquals("NAV_NO", opprettJournalpostRequest.kanal)
        assertEquals("string", opprettJournalpostRequest.sak?.arkivsaksnummer)
        assertEquals("PSAK", opprettJournalpostRequest.sak?.arkivsaksystem)
        assertEquals(Tema.PENSJON, opprettJournalpostRequest.tema)
        assertEquals("Inngående P2000 - Krav om alderspensjon", opprettJournalpostRequest.tittel)
    }

    @Test
    fun `Gitt en gyldig journalpostResponse json når mapping så skal alle felter mappes`() {
        val opprettjournalpostResponseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json")))
        val opprettJournalpostResponse = mapJsonToAny(opprettjournalpostResponseJson, typeRefs<OpprettJournalPostResponse>())
        assertEquals(opprettJournalpostResponse.journalpostId, "429434378")
        assertEquals(opprettJournalpostResponse.journalstatus, "M")
        assertEquals(opprettJournalpostResponse.melding, "null")
    }
}
