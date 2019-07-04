package no.nav.eessi.pensjon.journalforing.journalforing

import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.journalpost.BUCTYPE
import no.nav.eessi.pensjon.journalforing.services.journalpost.IdType.*
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class JournalforingServiceTest {

    @Mock
    private lateinit var mockEuxService: EuxService
    @Mock
    private lateinit var mockJournalpostService: JournalpostService
    @Mock
    private lateinit var mockOppgaveService: OppgaveService
    @Mock
    private lateinit var mockAktoerregisterService: AktoerregisterService
    @Mock
    private lateinit var mockPersonV3Service: PersonV3Service
    @Mock
    private lateinit var mockFagmodulService: FagmodulService
    @Mock
    private lateinit var mockOppgaveRoutingService: OppgaveRoutingService

    private lateinit var journalforingService: JournalforingService
    private lateinit var sedHendelse: SedHendelseModel

    @Before
    fun setup() {
        journalforingService = JournalforingService(
                mockEuxService,
                mockJournalpostService,
                mockOppgaveService,
                mockAktoerregisterService,
                mockPersonV3Service,
                mockFagmodulService,
                mockOppgaveRoutingService)
    }

    @Test
    fun `Gitt gyldig SedHendelse så bygg Gyldig JournalpostModel`() {
        sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))

        val journalpostRequest = journalforingService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.behandlingstema, BUCTYPE.valueOf(sedHendelse.bucType.toString()).BEHANDLINGSTEMA, "Ugyldig behandlingstema")
        assertEquals(journalpostRequest.journalpostRequest.bruker?.id, sedHendelse.navBruker, "Ugyldig bruker id")
        assertEquals(journalpostRequest.journalpostRequest.dokumenter.first().brevkode, sedHendelse.sedId, "Ugyldig brevkode")
        assertEquals(journalpostRequest.journalpostRequest.dokumenter.first().dokumentvarianter.first().fysiskDokument, "JVBERi0xLjQKJeLjz9MKMiAwIG9iago8PC9BbHRlcm5hdGUvRGV2aWNlUkdCL04gMy9MZW5ndGggMjU5Ni9G", "Ugyldig fysisk dokument")
        assertEquals(journalpostRequest.journalpostRequest.tema, BUCTYPE.valueOf(sedHendelse.bucType.toString()).TEMA, "Ugyldig tema")
        assertEquals(journalpostRequest.journalpostRequest.tittel,"Utgående ${sedHendelse.sedType}", "Ugyldig tittel")
    }

    @Test
    fun `Gitt utgående SED med fnr når populerer request så blir avsenderMottaker FNR`() {
        sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))

        val journalpostRequest = journalforingService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, FNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt utgående SED uten fnr når populerer request så blir avsenderMottaker NAV`() {
        sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))))


        val journalpostRequest = journalforingService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.SENDT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.avsenderId, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, sedHendelse.avsenderNavn, "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, ORGNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt inngående SED med fnr når populerer request så blir avsenderMottaker FNR`() {
        sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json"))))

        val journalpostRequest = journalforingService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.MOTTATT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.navBruker, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, "navn navnesen", "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, FNR, "Ugyldig idType")
    }

    @Test
    fun `Gitt inngående SED uten fnr når populerer request så blir avsenderMottaker utenlandsk ORG`() {
        sedHendelse = SedHendelseModel.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_03.json"))))

        val journalpostRequest = journalforingService.byggJournalPostRequest(sedHendelseModel= sedHendelse,
                sedDokumenter = SedDokumenterResponse.fromJson(String(Files.readAllBytes(Paths.get("src/test/resources/pdf/pdfResponseUtenVedlegg.json")))),
                sedHendelseType = HendelseType.MOTTATT,
                personNavn = "navn navnesen")

        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.id, sedHendelse.mottakerId, "Ugyldig mottagerid")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.navn, sedHendelse.mottakerNavn, "Ugyldig mottagernavn")
        assertEquals(journalpostRequest.journalpostRequest.avsenderMottaker.idType, UTL_ORG, "Ugyldig idType")
    }
}