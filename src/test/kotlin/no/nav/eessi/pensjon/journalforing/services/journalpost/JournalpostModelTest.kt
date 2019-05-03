package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JournalpostModelTest {

    @Test
    fun `Gitt en gyldig journalpost json når mapping så skal alle felter mappes`() {

        val mapper = jacksonObjectMapper()
        val journalpostJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/journalpost.json")))
        val journalpostModel = mapper.readValue(journalpostJson, JournalpostModel::class.java)
        assertEquals(journalpostModel.avsenderMottaker.id , "string")
        assertEquals(journalpostModel.avsenderMottaker.land, "string")
        assertEquals(journalpostModel.avsenderMottaker.navn, "string")
        assertEquals(journalpostModel.behandlingstema, "ab0001")
        assertEquals(journalpostModel.bruker.id, "string")
        assertEquals(journalpostModel.bruker.idType, "FNR")
        assertEquals(journalpostModel.dokumenter.first().brevkode, "NAV 14-05.09")
        assertEquals(journalpostModel.dokumenter.first().dokumentKategori, "SOK")
        assertEquals(journalpostModel.dokumenter.first().dokumentvarianter.first().filtype, "PDF/A")
        assertEquals(journalpostModel.dokumenter.first().dokumentvarianter.first().fysiskDokument, "string")
        assertEquals(journalpostModel.dokumenter.first().dokumentvarianter.first().variantformat, "ARKIV")
        assertEquals(journalpostModel.dokumenter.first().tittel, "Søknad om foreldrepenger ved fødsel")
        assertEquals(journalpostModel.eksternReferanseId, "string")
        assertEquals(journalpostModel.journalfoerendeEnhet, 9999)
        assertEquals(journalpostModel.journalpostType, "INNGAAENDE")
        assertEquals(journalpostModel.kanal, "NAV_NO")
        assertEquals(journalpostModel.sak.arkivsaksnummer, "string")
        assertEquals(journalpostModel.sak.arkivsaksystem, "GSAK")
        assertEquals(journalpostModel.tema, "FOR")
        assertEquals(journalpostModel.tilleggsopplysninger?.first()?.nokkel, "string")
        assertEquals(journalpostModel.tilleggsopplysninger?.first()?.verdi, "string")
        assertEquals(journalpostModel.tittel, "Ettersendelse til søknad om foreldrepenger")
    }
}
