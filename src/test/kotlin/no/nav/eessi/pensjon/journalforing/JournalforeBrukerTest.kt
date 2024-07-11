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

    @BeforeEach
    fun setUp() {
        safClient = mockk()
        gcpStorageService = mockk()
        journalpostService = mockk()
        oppgaveHandler = mockk()
        metricsHelper = MetricsHelper.ForTest()

        journalforeBruker = spyk(JournalforeBruker(safClient, gcpStorageService, journalpostService, oppgaveHandler, metricsHelper))
    }

    @Test
    fun `journalpostId skal hentes fra lagring og benyttes til ved opprettelse av journalpost og ferdigstiller `() {
        val journalpostRequest: OpprettJournalpostRequest = mockk()

        val rinaId = "111111"
        val dokumentId = "222222"
        val sedHendelse: SedHendelse = mockk {
            every { rinaSakId } returns rinaId
            every { rinaDokumentId } returns dokumentId
        }
        val identifisertPerson: IdentifisertPerson = mockk()
        val bruker: Bruker = mockk()

        val journalpostId = "journalpostId"
        val journalforendeEnhet = "journalforendeEnhet"

        every { gcpStorageService.arkiverteSakerForRinaId(rinaId, dokumentId) } returns listOf(rinaId)
        every { gcpStorageService.hentOpprettJournalpostRequest(rinaId) } returns Pair(journalpostId, mockk())
        every { safClient.hentJournalpost(journalpostId) } returns createTestJournalpostResponse(journalpostId = journalpostId, journalforendeEnhet = journalforendeEnhet)

        every { journalforeBruker.oppdaterOppgave(rinaId, any(), journalpostRequest, sedHendelse, identifisertPerson) } just Runs
        every { journalforeBruker.oppdaterJournalpost(any(), journalpostRequest, bruker) } just Runs
        every { journalpostService.ferdigstilljournalpost(journalpostId, journalforendeEnhet) } just Runs
        every { gcpStorageService.slettJournalpostDetaljer(any()) } just Runs

        journalforeBruker.journalpostMedBruker(journalpostRequest, sedHendelse, identifisertPerson, bruker)

        verify(exactly = 1) { gcpStorageService.arkiverteSakerForRinaId(rinaId, dokumentId) }
        verify(exactly = 1) { gcpStorageService.hentOpprettJournalpostRequest(rinaId) }
        verify(exactly = 1) { safClient.hentJournalpost(journalpostId) }
        verify(exactly = 1) { journalforeBruker.oppdaterOppgave(rinaId, any(), journalpostRequest, sedHendelse, identifisertPerson) }
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