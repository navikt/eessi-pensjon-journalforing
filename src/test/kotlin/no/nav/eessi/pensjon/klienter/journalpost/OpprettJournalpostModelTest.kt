package no.nav.eessi.pensjon.klienter.journalpost

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Tema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class OpprettJournalpostModelTest {
    @Test
    fun `Test at serialisering og deserialisering fungerer som forventet`() {
        val actualRequest = OpprettJournalpostRequest(
                AvsenderMottaker(land = "GB"),
                Behandlingstema.ALDERSPENSJON.decode(),
                Bruker("brukerId"),
                "[]",
                "eksternRefId",
                Enhet.ID_OG_FORDELING.enhetsNr,
                JournalpostType.INNGAAENDE,
                "EESSI",
                Sak("arkivsaksnummer123", "arksys2"),
                Tema.PENSJON.decode(),
                emptyList(),
                "tittel på sak")

        val serialized = actualRequest.toJson()

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
        val opprettjournalpostResponseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json")))
        val opprettJournalpostResponse = mapJsonToAny(opprettjournalpostResponseJson, typeRefs<OpprettJournalPostResponse>())
        assertEquals(opprettJournalpostResponse.journalpostId, "429434378")
        assertEquals(opprettJournalpostResponse.journalstatus, "M")
        assertEquals(opprettJournalpostResponse.melding, "null")
    }
}
