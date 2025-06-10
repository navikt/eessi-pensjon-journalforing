package no.nav.eessi.pensjon.klienter.journalpost

import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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
                ID_OG_FORDELING,
                JournalpostType.INNGAAENDE,
                Sak("FAGSAK", "11111", "PEN"),
                Tema.PENSJON,
                emptyList(),
                "tittel på sak")

        val serialized = actualRequest.toJson()

        assertTrue(serialized.contains("\"behandlingstema\" : \"ab0254\""))
        assertTrue(serialized.contains("\"journalfoerendeEnhet\" : \"4303\""))
        assertTrue(serialized.contains("\"journalpostType\" : \"INNGAAENDE\""))
        assertTrue(serialized.contains("\"tema\" : \"PEN\""))

        val deserialized = mapJsonToAny<OpprettJournalpostRequest>(serialized)

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

        val opprettJournalpostRequestJson = javaClass.getResource("/journalpost/opprettJournalpostRequest.json")!!.readText()

        val opprettJournalpostRequest = mapJsonToAny<OpprettJournalpostRequest>((opprettJournalpostRequestJson))
        assertEquals(opprettJournalpostRequest.behandlingstema, Behandlingstema.ALDERSPENSJON)
        assertEquals(opprettJournalpostRequest.bruker?.id, "12345678912")
        assertEquals(opprettJournalpostRequest.bruker?.idType, "FNR")
        assertEquals(opprettJournalpostRequest.dokumenter, """[{"brevkode":"NAV 14-05.09","dokumentKategori":"SOK","dokumentvarianter":[{"filtype":"PDF/A","fysiskDokument":"string","variantformat":"ARKIV"}],"tittel":"Søknad om foreldrepenger ved fødsel"}]""")
        assertEquals(opprettJournalpostRequest.eksternReferanseId, "string")
        assertEquals(opprettJournalpostRequest.journalfoerendeEnhet, UFORE_UTLAND)
        assertEquals(opprettJournalpostRequest.journalpostType, JournalpostType.INNGAAENDE)
        assertEquals(opprettJournalpostRequest.kanal, "NAV_NO")
        assertEquals(opprettJournalpostRequest.sak?.sakstype, "FAGSAK")
        assertEquals(opprettJournalpostRequest.sak?.fagsakid, "11111111")
        assertEquals(opprettJournalpostRequest.sak?.fagsaksystem, "PP01")
        assertEquals(opprettJournalpostRequest.tema, Tema.PENSJON)
        assertEquals(opprettJournalpostRequest.tittel, "Inngående P2000 - Krav om alderspensjon")
    }

    @Test
    fun `Gitt en gyldig journalpostResponse json når mapping så skal alle felter mappes`() {
        val opprettjournalpostResponseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponseFalse.json")))
        val opprettJournalpostResponse = mapJsonToAny<OpprettJournalPostResponse>(opprettjournalpostResponseJson)
        assertEquals(opprettJournalpostResponse.journalpostId, "429434378")
        assertEquals(opprettJournalpostResponse.journalstatus, "M")
        assertEquals(opprettJournalpostResponse.melding, "null")
    }
}
