package no.nav.eessi.pensjon.journalforing

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.journalforing.saf.SafDokument
import no.nav.eessi.pensjon.journalforing.saf.SafSak
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JournalforeBrukerTest {

    private lateinit var safClient: SafClient
    private lateinit var gcpStorageService: GcpStorageService
    private lateinit var journalpostService: JournalpostService
    private lateinit var oppgaveHandler: OppgaveHandler
    private lateinit var metricsHelper: MetricsHelper
    private lateinit var journalforeBruker: JournalforeBruker

    private val journalpostRequest: OpprettJournalpostRequest = mockk()
    private val rinaId = "111111"
    private val dokumentId = "222222"
    private val sedHendelse: SedHendelse = mockk {
        every { rinaSakId } returns rinaId
        every { rinaDokumentId } returns dokumentId
    }
    private val identifisertPerson: IdentifisertPerson = mockk()
    private val bruker: Bruker = mockk()

    private val journalpostId = "journalpostId"
    private val journalforendeEnhet = "journalforendeEnhet"

    @BeforeEach
    fun setUp() {
        safClient = mockk()
        gcpStorageService = mockk()
        journalpostService = mockk()
        oppgaveHandler = mockk()
        metricsHelper = MetricsHelper.ForTest()

        journalforeBruker = spyk(JournalforeBruker(safClient, gcpStorageService, journalpostService, oppgaveHandler, metricsHelper))

        every { gcpStorageService.arkiverteSakerForRinaId(rinaId, dokumentId) } returns listOf(rinaId)
        every { gcpStorageService.hentOpprettJournalpostRequest(rinaId) } returns Pair(journalpostId, mockk())
        every { safClient.hentJournalpost(journalpostId) } returns createTestJournalpostResponse(journalpostId = journalpostId, journalforendeEnhet = journalforendeEnhet)

//        every { journalforeBruker.oppdaterOppgave(rinaId, any(), journalpostRequest, sedHendelse, identifisertPerson) } just Runs
        every { journalforeBruker.oppdaterJournalpost(any(), journalpostRequest, bruker) } just Runs
        every { gcpStorageService.slettJournalpostDetaljer(any()) } just Runs
    }

    @Test
    fun `journalpostId skal hentes fra GCP deretter lage journalpost og ferdigstille uten aa lage oppgave`() {

        every { journalpostService.ferdigstilljournalpost(journalpostId, journalforendeEnhet) } returns JournalpostModel.Ferdigstilt("Ferdigstilt, ingen oppgave")
        journalforeBruker.journalpostMedBruker(journalpostRequest, sedHendelse, identifisertPerson, bruker)

        verify(exactly = 1) { gcpStorageService.arkiverteSakerForRinaId(rinaId, dokumentId) }
        verify(exactly = 1) { gcpStorageService.hentOpprettJournalpostRequest(rinaId) }
        verify(exactly = 1) { safClient.hentJournalpost(journalpostId) }
        //verify(exactly = 1) { journalforeBruker.oppdaterOppgave(rinaId, any(), journalpostRequest, sedHendelse, identifisertPerson) }
//        verify(exactly = 1) { journalforeBruker.opprettOppgave(sedHendelse, any(), identifisertPerson, journalpostRequest) }
        verify(exactly = 1) { journalforeBruker.oppdaterJournalpost(any(), journalpostRequest, bruker) }
        verify(exactly = 1) { journalpostService.ferdigstilljournalpost(journalpostId, journalforendeEnhet) }
        verify(exactly = 1) { gcpStorageService.slettJournalpostDetaljer(any()) }
    }

    @Test
    fun `journalpostId skal hentes fra GCP deretter lage journalpost og lage oppgave`() {

        every { journalpostService.ferdigstilljournalpost(journalpostId, journalforendeEnhet) } returns JournalpostModel.IngenFerdigstilling("Ingen ferdigstilling, lager oppgave")
        every { journalforeBruker.opprettOppgave(sedHendelse, any(), identifisertPerson, journalpostRequest) } just Runs

        journalforeBruker.journalpostMedBruker(journalpostRequest, sedHendelse, identifisertPerson, bruker)

        verify(exactly = 1) { gcpStorageService.arkiverteSakerForRinaId(rinaId, dokumentId) }
        verify(exactly = 1) { gcpStorageService.hentOpprettJournalpostRequest(rinaId) }
        verify(exactly = 1) { safClient.hentJournalpost(journalpostId) }
        //verify(exactly = 1) { journalforeBruker.oppdaterOppgave(rinaId, any(), journalpostRequest, sedHendelse, identifisertPerson) }
        verify(exactly = 1) { journalforeBruker.opprettOppgave(sedHendelse, any(), identifisertPerson, journalpostRequest) }
        verify(exactly = 1) { journalforeBruker.oppdaterJournalpost(any(), journalpostRequest, bruker) }
        verify(exactly = 1) { journalpostService.ferdigstilljournalpost(journalpostId, journalforendeEnhet) }
        verify(exactly = 1) { gcpStorageService.slettJournalpostDetaljer(any()) }
    }

    fun createTestJournalpostResponse(
        journalpostId: String? = "defaultJournalpostId",
        eksternReferanseId: String? = "defaultEksternReferanseId",
        tema: Tema? = Tema.UFORETRYGD,
        dokumenter: List<SafDokument?> = listOf(createTestSafDokument()),
        journalstatus: Journalstatus? = Journalstatus.UNDER_ARBEID,
        journalpostferdigstilt: Boolean? = false,
        avsenderMottaker: AvsenderMottaker? = createTestAvsenderMottaker(),
        behandlingstema: Behandlingstema? = Behandlingstema.UFOREPENSJON,
        journalforendeEnhet: String? = "defaultEnhet",
        temanavn: String? = "defaultTemanavn",
        bruker: Bruker? = createTestBruker(),
        sak: SafSak? = createTestSafSak(),
        datoOpprettet: LocalDateTime? = LocalDateTime.now()
    ): JournalpostResponse {
        return JournalpostResponse(
            journalpostId = journalpostId,
            eksternReferanseId = eksternReferanseId,
            tema = tema,
            dokumenter = dokumenter,
            journalstatus = journalstatus,
            journalpostferdigstilt = journalpostferdigstilt,
            avsenderMottaker = avsenderMottaker,
            behandlingstema = behandlingstema,
            journalforendeEnhet = journalforendeEnhet,
            temanavn = temanavn,
            bruker = bruker,
            sak = sak,
            datoOpprettet = datoOpprettet
        )
    }

    fun createTestSafDokument(
        dokumentInfoId: String? = "defaultDokumentInfoId",
        tittel: String? = "defaultTittel",
        brevkode: String? = "defaultBrevkode"
    ): SafDokument {
        return SafDokument(
            dokumentInfoId = dokumentInfoId!!,
            tittel = tittel!!,
            brevkode = brevkode
        )
    }

    fun createTestAvsenderMottaker(
        id: String? = "defaultId",
        navn: String? = "defaultNavn",
    ): AvsenderMottaker {
        return AvsenderMottaker(
            id = id,
            navn = navn
        )
    }

    fun createTestBruker(
        id: String? = "defaultId"
    ): Bruker {
        return Bruker(
            id = id!!,
           "type"
        )
    }

    fun createTestSafSak(
        fagsakId: String? = "defaultFagsakId",
        fagsaksystem: String? = "defaultFagsaksystem"
    ): SafSak {
        return SafSak(
            fagsakId = fagsakId,
            fagsaksystem = fagsaksystem
        )
    }
}