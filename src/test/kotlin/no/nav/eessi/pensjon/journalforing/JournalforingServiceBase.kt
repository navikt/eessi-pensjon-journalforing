package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Service
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsHandler
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
private val STERK_BUSK = Fodselsnummer.fra("12011577847")!!
abstract class JournalforingServiceBase {

    val journalpostKlient = mockk<JournalpostKlient>()
    val journalpostService = JournalpostService(journalpostKlient)
    val pdfService = mockk<PDFService>()
    val oppgaveHandler = mockk<OppgaveHandler>(relaxUnitFun = true)
    val kravHandeler = mockk<KravInitialiseringsHandler>()
    val gcpStorageService = mockk<GcpStorageService>(relaxed = true)
    val kravService = KravInitialiseringsService(kravHandeler)

    protected val norg2Service = mockk<Norg2Service> {
        every { hentArbeidsfordelingEnhet(any()) } returns null
    }
    private val automatiseringHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    val statistikkPublisher = StatistikkPublisher(automatiseringHandlerKafka)
    val oppgaveRoutingService = OppgaveRoutingService(norg2Service)

    val journalforingService = JournalforingService(
        journalpostService,
        oppgaveRoutingService,
        pdfService,
        oppgaveHandler,
        kravService,
        gcpStorageService,
        statistikkPublisher,
        mockk(relaxed = true)
    )

    protected val opprettJournalpostRequestCapturingSlot = slot<OpprettJournalpostRequest>()
    @BeforeEach
    fun setup() {
        journalforingService.nameSpace = "test"

        //MOCK RESPONSES
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), any()) } returns Pair("Supported Documents", emptyList())
        every { gcpStorageService.journalFinnes(any()) } returns false
        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "EKSPEDERT",
            melding = "",
            journalpostferdigstilt = false,
        )

        every { journalpostKlient.opprettJournalpost(capture(opprettJournalpostRequestCapturingSlot), any(), null) } returns opprettJournalPostResponse
    }

    fun identifisertPersonPDL(
        aktoerId: String = AKTOERID,
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPDLPerson =
        IdentifisertPDLPerson(
            aktoerId,
            landkode,
            geografiskTilknytning,
            personRelasjon,
            fnr,
            personNavn = personNavn,
            identer = null
        )

    fun sedPersonRelasjon(fnr: Fodselsnummer? = LEALAUS_KAKE, relasjon: Relasjon = Relasjon.FORSIKRET, rinaDocumentId: String = RINADOK_ID) =
        SEDPersonRelasjon(fnr = fnr, relasjon = relasjon, rinaDocumentId = rinaDocumentId)

    private fun navAnsattInfo(): Pair<String, Enhet?>? = null

}