package no.nav.eessi.pensjon.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import no.nav.eessi.pensjon.services.journalpost.IdType.FNR

class ModelsTest {

    @Test
    fun `Gitt en gyldig journalpostRequest json når mapping så skal alle felter mappes`() {

        val mapper = jacksonObjectMapper()
        val journalpostRequestJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json")))

        val journalpostRequestModel = mapper.readValue(journalpostRequestJson, JournalpostRequest::class.java)
        assertEquals(journalpostRequestModel.avsenderMottaker.id , "12345678912")
        assertEquals(journalpostRequestModel.avsenderMottaker.idType, FNR)
        assertEquals(journalpostRequestModel.behandlingstema, "ab0254")
        assertEquals(journalpostRequestModel.bruker?.id, "12345678912")
        assertEquals(journalpostRequestModel.bruker?.idType, "FNR")
        assertEquals(journalpostRequestModel.dokumenter, """[{"brevkode":"NAV 14-05.09","dokumentKategori":"SOK","dokumentvarianter":[{"filtype":"PDF/A","fysiskDokument":"string","variantformat":"ARKIV"}],"tittel":"Søknad om foreldrepenger ved fødsel"}]""")
        assertEquals(journalpostRequestModel.eksternReferanseId, "string")
        assertEquals(journalpostRequestModel.journalfoerendeEnhet, "9999")
        assertEquals(journalpostRequestModel.journalpostType, JournalpostType.INNGAAENDE)
        assertEquals(journalpostRequestModel.kanal, "NAV_NO")
        assertEquals(journalpostRequestModel.sak?.arkivsaksnummer, "string")
        assertEquals(journalpostRequestModel.sak?.arkivsaksystem, "GSAK")
        assertEquals(journalpostRequestModel.tema, "PEN")
        assertEquals(journalpostRequestModel.tittel, "Inngående P2000 - Krav om alderspensjon")
    }

    @Test
    fun `Gitt en gyldig journalpostResponse json når mapping så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val journalpostResponseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json")))
        val journalpostResponseModel = mapper.readValue(journalpostResponseJson, JournalPostResponse::class.java)
        assertEquals(journalpostResponseModel.journalpostId, "429434378")
        assertEquals(journalpostResponseModel.journalstatus, "M")
        assertEquals(journalpostResponseModel.melding, "null")
    }
}
