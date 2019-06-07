package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

class ModelsTest {

    @Test
    fun `Gitt en gyldig journalpostRequest json når mapping så skal alle felter mappes`() {

        val mapper = jacksonObjectMapper()
        val journalpostRequestJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostRequest.json")))
        val journalpostRequestModel = mapper.readValue(journalpostRequestJson, JournalpostRequest::class.java)
        assertEquals(journalpostRequestModel.avsenderMottaker?.id , "string")
        assertEquals(journalpostRequestModel.avsenderMottaker?.land, "string")
        assertEquals(journalpostRequestModel.avsenderMottaker?.navn, "string")
        assertEquals(journalpostRequestModel.behandlingstema, "ab0001")
        assertEquals(journalpostRequestModel.bruker?.id, "string")
        assertEquals(journalpostRequestModel.bruker?.idType, "FNR")
        assertEquals(journalpostRequestModel.dokumenter.first().brevkode, "NAV 14-05.09")
        assertEquals(journalpostRequestModel.dokumenter.first().dokumentKategori, "SOK")
        assertEquals(journalpostRequestModel.dokumenter.first().dokumentvarianter.first().filtype, "PDF/A")
        assertEquals(journalpostRequestModel.dokumenter.first().dokumentvarianter.first().fysiskDokument, "string")
        assertEquals(journalpostRequestModel.dokumenter.first().dokumentvarianter.first().variantformat, Variantformat.ARKIV)
        assertEquals(journalpostRequestModel.dokumenter.first().tittel, "Søknad om foreldrepenger ved fødsel")
        assertEquals(journalpostRequestModel.eksternReferanseId, "string")
        assertEquals(journalpostRequestModel.journalfoerendeEnhet, "9999")
        assertEquals(journalpostRequestModel.journalpostType, "INNGAAENDE")
        assertEquals(journalpostRequestModel.kanal, "NAV_NO")
        assertEquals(journalpostRequestModel.sak?.arkivsaksnummer, "string")
        assertEquals(journalpostRequestModel.sak?.arkivsaksystem, "GSAK")
        assertEquals(journalpostRequestModel.tema, "FOR")
        assertEquals(journalpostRequestModel.tittel, "Ettersendelse til søknad om foreldrepenger")
    }

    @Test
    fun `Gitt en gyldig journalpostResponse json når mapping så skal alle felter mappes`() {
        val mapper = jacksonObjectMapper()
        val journalpostResponseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpostResponse.json")))
        val journalpostResponseModel = mapper.readValue(journalpostResponseJson, JournalPostResponse::class.java)
        assertEquals(journalpostResponseModel.journalpostId, "string")
        assertEquals(journalpostResponseModel.journalstatus, "MIDLERTIDIG")
        assertEquals(journalpostResponseModel.melding, "string")
    }
}