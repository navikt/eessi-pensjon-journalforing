package no.nav.eessi.pensjon.journalforing.journalforing

import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.IdType.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

class JournalpostModelTest {

    @Test
    fun `Gitt gyldig SedHendelse så bygg Gyldig JournalpostModel`() {
        val sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))

        val journalpostModel = JournalpostModel.from(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostModel.journalpostRequest.behandlingstema, sedHendelse.bucType?.BEHANDLINGSTEMA, "Ugyldig behandlingstema")
        assertEquals(journalpostModel.journalpostRequest.bruker?.id, sedHendelse.navBruker, "Ugyldig bruker id")
        assertEquals(journalpostModel.journalpostRequest.dokumenter.first().brevkode, sedHendelse.sedId, "Ugyldig brevkode")
        assertEquals(journalpostModel.journalpostRequest.dokumenter.first().dokumentvarianter.first().fysiskDokument, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G", "Ugyldig fysisk dokument")
        assertEquals(journalpostModel.journalpostRequest.tema, sedHendelse.bucType?.TEMA, "Ugyldig tema")
        assertEquals(journalpostModel.journalpostRequest.tittel,"Utgående ${sedHendelse.sedType}", "Ugyldig tittel")
    }

    @Test
    fun `Gitt utgående SED med fnr når populerer request så blir avsenderMottaker FNR`() {
        val sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))

        val journalpostModel = JournalpostModel.from(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.idType, FNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt utgående SED uten fnr når populerer request så blir avsenderMottaker NAV`() {
        val sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))))


        val journalpostModel = JournalpostModel.from(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.id, sedHendelse.avsenderId, "Ugyldig mottagerid")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.navn, sedHendelse.avsenderNavn, "Ugyldig mottagernavn")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.idType, ORGNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt inngående SED med fnr når populerer request så blir avsenderMottaker FNR`() {
        val sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))

        val journalpostModel = JournalpostModel.from(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.MOTTATT,
                personNavn = "navn navnesen")

        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.idType, FNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt inngående SED uten fnr når populerer request så blir avsenderMottaker utenlandsk ORG`() {
        val sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))))

        val journalpostModel = JournalpostModel.from(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.MOTTATT,
                personNavn = "navn navnesen")

        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.id, sedHendelse.mottakerId, "Ugyldig mottagerid")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.navn, sedHendelse.mottakerNavn, "Ugyldig mottagernavn")
        assertEquals(journalpostModel.journalpostRequest.avsenderMottaker.idType, UTL_ORG, "Ugyldig idType")
    }
}