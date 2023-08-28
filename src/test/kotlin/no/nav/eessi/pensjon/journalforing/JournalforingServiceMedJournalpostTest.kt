package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
internal class JournalforingServiceMedJournalpostTest {

    private val journalpostKlient = mockk<JournalpostKlient>(relaxUnitFun = true)
    private val journalpostService = JournalpostService(journalpostKlient)
    private val pdfService = mockk<PDFService>()
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxUnitFun = true)
    private val kravHandeler = mockk<KravInitialiseringsHandler>()
    private val kravService = KravInitialiseringsService(kravHandeler)

    private val norg2Service = mockk<Norg2Service> {
        every { hentArbeidsfordelingEnhet(any()) } returns null
    }
    protected val automatiseringHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val automatiseringStatistikkPublisher = AutomatiseringStatistikkPublisher(automatiseringHandlerKafka)
    private val oppgaveRoutingService = OppgaveRoutingService(norg2Service)

    private val journalforingService = JournalforingService(
        journalpostService,
        oppgaveRoutingService,
        pdfService,
        oppgaveHandler,
        kravService,
        automatiseringStatistikkPublisher
    )

    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
    }

    @BeforeEach
    fun setup() {
        journalforingService.initMetrics()

        journalforingService.nameSpace = "test"
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P6000) } returns Pair("P6000 Supported Documents", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2000) } returns Pair("P2000 Age Pension", emptyList())
    }

    @Test
    fun `Sendt P6000 med all infor for forsoekFerdigstill true skal populere Journalpostresponsen med pesys sakid`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val saksInformasjon = SakInformasjon(sakId = "22874955", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)

        val requestSlot = slot<OpprettJournalpostRequest>()
        val forsoekFedrigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), capture(forsoekFedrigstillSlot)) } returns mockk(relaxed = true)


        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SakType.ALDER,
            0,
            sakInformasjon = saksInformasjon,
            SED(type = SedType.P6000),
            identifisertePersoner = 1,
            pesysSakId = false,
        )
        val journalpostRequest = requestSlot.captured
        val erMuligAaFerdigstille = forsoekFedrigstillSlot.captured

        println(journalpostRequest)

        Assertions.assertEquals("22874955", journalpostRequest.sak?.arkivsaksnummer)
        Assertions.assertEquals(true, erMuligAaFerdigstille)

    }

    @Test
    fun `Sendt P6000 med manglende saksinfo skal returnere false på forsoekFerdigstill`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val forsoekFerdigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(any(), capture(forsoekFerdigstillSlot)) } returns mockk(relaxed = true)


        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            0,
            sakInformasjon = null,
            SED(type = SedType.P6000),
            identifisertePersoner = 1,
            pesysSakId = false,
        )
        val erMuligAaFerdigstille = forsoekFerdigstillSlot.captured

        Assertions.assertEquals(false, erMuligAaFerdigstille)

    }

    @Disabled
    @Test
    fun `Innkommende P2000 fra Sverige som oppfyller alle krav til automatisk journalføring skal opprette behandle SED oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_SE.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val saksInformasjon = SakInformasjon(sakId = "22874955", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)

        val requestSlot = slot<OpprettJournalpostRequest>()
        val forsoekFedrigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), capture(forsoekFedrigstillSlot)) } returns mockk(relaxed = true)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SakType.ALDER,
            0,
            sakInformasjon = saksInformasjon,
            SED(type = SedType.P2000),
            identifisertePersoner = 1,
            pesysSakId = false,
        )
        val journalpostRequest = requestSlot.captured
        val erMuligAaFerdigstille = forsoekFedrigstillSlot.captured

        println(journalpostRequest)

        verify(exactly = 1) { journalforingService.opprettBehandleSedOppgave(any(), any(), any(), any()) }

        Assertions.assertEquals("22874955", journalpostRequest.sak?.arkivsaksnummer)
        Assertions.assertEquals(true, erMuligAaFerdigstille)
        Assertions.assertEquals(Enhet.AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

    }

    fun identifisertPersonPDL(
        aktoerId: String = AKTOERID,
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPersonPDL =
        IdentifisertPersonPDL(aktoerId, landkode, geografiskTilknytning, personRelasjon, fnr, personNavn = personNavn)

    fun sedPersonRelasjon(fnr: Fodselsnummer? = LEALAUS_KAKE, relasjon: Relasjon = Relasjon.FORSIKRET, rinaDocumentId: String = RINADOK_ID) =
        SEDPersonRelasjon(fnr = fnr, relasjon = relasjon, rinaDocumentId = rinaDocumentId)
}