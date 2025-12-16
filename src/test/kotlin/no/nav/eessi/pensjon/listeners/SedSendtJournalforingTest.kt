package no.nav.eessi.pensjon.listeners

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.eux.EuxCacheableKlient
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.SedType.P8000
import no.nav.eessi.pensjon.eux.model.buc.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase.Companion.FNR_VOKSEN_UNDER_62
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Klient
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Service
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType.JOURNALFORING_UT
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OpprettOppgaveService
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.navansatt.NavansattKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.GJENLEVENDE
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import org.springframework.web.client.RestTemplate
import java.time.LocalDate

private const val PESYS_SAKID = "25595454"
private const val RINA_ID = "148161"
private const val AKTOER_ID = "3216549873212"

internal class SedSendtJournalforingTest {
    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)

    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val norg2Service = Norg2Service(mockk<Norg2Klient>())
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val euxKlientLib = mockk<EuxKlientLib>(relaxed = true)
    private val euxCacheableKlient = EuxCacheableKlient(euxKlientLib)
    private val euxService = EuxService(euxCacheableKlient)
    private val bestemSakKlient = mockk<BestemSakKlient>(relaxed = true)
    private val fagmodulKlient = mockk<FagmodulKlient>(relaxed = true)
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxed = true)

    private val opprettOppgaveService = OpprettOppgaveService(oppgaveHandler)
    private val journalpostKlient = mockk<JournalpostKlient>(relaxed = true)
    private val gcpStorageService = mockk<GcpStorageService>()
    private val etterlatteService = mockk<EtterlatteService>()

    private val hentSakService = HentSakService(etterlatteService, gcpStorageService)
    private lateinit var pdfService : PDFService

    private lateinit var sedListener : SedSendtListener
    private lateinit var hentTemaService: HentTemaService
    private lateinit var journalpostService : JournalpostService
    private lateinit var jouralforingService: JournalforingService

    private val buc = Buc(id = PESYS_SAKID, documents = listOf(DocumentsItem(
                type = P8000,
                id = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1",
                status = "sent",
                direction = "OUT"
            )), participants = listOf(Participant(
                role = "CaseOwner",
                organisation = Organisation(countryCode = "NO")
            ))
    )

    @BeforeEach
    fun setup() {

        pdfService = mockk<PDFService>(relaxed = true).also {
            every { it.hentDokumenterOgVedlegg(any(), any(), any()) } returns Pair("1234568", emptyList())
        }
        journalpostService = JournalpostService(journalpostKlient, pdfService, opprettOppgaveService)
        hentTemaService = HentTemaService(journalpostService, gcpStorageService)
        jouralforingService = JournalforingService(
            journalpostService = journalpostService,
            oppgaveRoutingService = OppgaveRoutingService(norg2Service),
            kravInitialiseringsService = mockk(),
            statistikkPublisher = mockk<StatistikkPublisher>(relaxed = true),
            gcpStorageService = gcpStorageService,
            hentSakService = hentSakService,
            hentTemaService = hentTemaService,
            oppgaveService = opprettOppgaveService,
            env = null,
        )

        sedListener = SedSendtListener(
            jouralforingService,
            personidentifiseringService,
            euxService,
            FagmodulService(fagmodulKlient),
            BestemSakService(bestemSakKlient),
            NavansattKlient(mockk<RestTemplate>(relaxed = true)),
            gcpStorageService = gcpStorageService,
            "test"
        )
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { etterlatteService.hentGjennySak(any()) } returns JournalforingTestBase.mockHentGjennySak("123456789")
    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt med saktype GJENLEV fra pensjonsinformasjon returnerer sakInformasjo saktype GJENLEV og oppretter journalpost med maskinell journalforing`() {
        val buc = Buc(id = RINA_ID, documents = listOf(DocumentsItem(type = P8000, id = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1", status = "sent", direction = "OUT")),
            participants = listOf(Participant(role = "CaseOwner", organisation = Organisation(countryCode = "NO")))
        )
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId = PESYS_SAKID,
            sakType = SakType.GJENLEV,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        val identifisertPerson = identifisertPersonPDL(
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62),
                relasjon = GJENLEVENDE,
                saktype = null,
                sedType = P8000,
                fdato = LocalDate.of(1971, 6, 11),
                rinaDocumentId = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
            ),
            fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62)
        )

        every { euxService.hentBuc(eq(RINA_ID)) } returns buc
        every { euxKlientLib.hentSedJson(eq(RINA_ID), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any(),) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any()) } returns LocalDate.of(1971, 6,11)
        every { fagmodulKlient.hentPensjonSaklist(eq(AKTOER_ID)) } returns listOf(sakInformasjon)
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }
        every { gcpStorageService.hentFraGjenny(any()) } returns null

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any(), null) } returns opprettJournalPostResponse

        val requestSlotOppgave = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(requestSlotOppgave)) }

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)

        val actualRequest = requestSlot.captured
        val actualRequestOppgave = requestSlotOppgave.captured

        Assertions.assertEquals(UFORE_UTLAND, actualRequest.journalfoerendeEnhet)
        Assertions.assertEquals(UFORE_UTLAND, actualRequestOppgave.tildeltEnhetsnr)
        Assertions.assertEquals(JOURNALFORING_UT, actualRequestOppgave.oppgaveType)

    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt ved en saktype vi ikke behandler rutes oppgave i hht til regler i journalforingsEnhet`() {
        // Denne oppgaven blir rutet til UFORE_UTLANDSTILSNITT siden det er en identifisert person under 62 årr (over 18) som er bosatt Norge
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId = PESYS_SAKID,
            sakType = SakType.AFP_PRIVAT,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        val identifisertPerson = identifisertPersonPDL(
            landkode = "NOR",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62),
                relasjon = GJENLEVENDE,
                saktype = null,
                sedType = P8000,
                fdato = LocalDate.of(1971, 6, 11),
                rinaDocumentId = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
            ),
            fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62)
        )

        every { euxService.hentBuc(eq(RINA_ID)) } returns buc
        every { euxKlientLib.hentSedJson(eq(RINA_ID), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any(),) } returns identifisertPerson
        every { personidentifiseringService.hentIdentifisertePersoner(any()) } returns listOf(identifisertPerson)
        every { personidentifiseringService.hentFodselsDato(any(), any()) } returns LocalDate.of(1971, 6, 11)
        every { gcpStorageService.gjennyFinnes(RINA_ID) } returns false
        every { fagmodulKlient.hentPensjonSaklist(eq(AKTOER_ID)) } returns listOf(sakInformasjon)
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }
        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "",
            melding = "Z990965",
            journalpostferdigstilt = false,
        )

        every { gcpStorageService.hentFraGjenny(any()) } returns null

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any(), any()) } returns opprettJournalPostResponse

        val requestSlotOppgave = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(requestSlotOppgave)) }

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)

        val actualRequest = requestSlot.captured
        val actualRequestOppgave = requestSlotOppgave.captured

        Assertions.assertEquals(UFORE_UTLANDSTILSNITT, actualRequest.journalfoerendeEnhet)
        Assertions.assertEquals(UFORE_UTLANDSTILSNITT, actualRequestOppgave.tildeltEnhetsnr)
        Assertions.assertEquals(JOURNALFORING_UT, actualRequestOppgave.oppgaveType)

    }

    @Test
    fun `Ved kall til pensjoninformasjon der det returneres to saker saa skal vi velge den som har samme pesys sakid M051`() {
        val sedJson = javaClass.getResource("/sed/M051.json")!!.readText()
        val sedHendelse = javaClass.getResource("/eux/hendelser/M_BUC_03a_M051.json")!!.readText()

        val sakInformasjon = listOf(
            SakInformasjon(
                sakId = "111111",
                sakType = SakType.UFOREP,
                sakStatus = SakStatus.LOPENDE,
                saksbehandlendeEnhetId = "",
                nyopprettet = false
            ), SakInformasjon(
                sakId = PESYS_SAKID,
                sakType = SakType.GJENLEV,
                sakStatus = SakStatus.OPPHOR,
                saksbehandlendeEnhetId = "",
                nyopprettet = false
            )
        )

        val identifisertPerson = identifisertPersonPDL(
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62),
                relasjon = GJENLEVENDE,
                saktype = null,
                sedType = P8000,
                fdato = LocalDate.of(1971, 6, 11),
                rinaDocumentId = "165sdugh587dfkgjhbkj"
            ),
            fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62)
        )

        every { euxService.hentBuc(any()) } returns buc
        every { euxKlientLib.hentBucJson(any()) } returns sedJson //TODO korrekt?
        every { euxKlientLib.hentSedJson(any(), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any(),) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any()) } returns LocalDate.of(1971, 6, 11)
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns sakInformasjon
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }
        every { gcpStorageService.hentFraGjenny(any()) } returns null

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "EKSPEDERT",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any(), null) } returns opprettJournalPostResponse

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)
        val actualRequest = requestSlot.captured

        Assertions.assertEquals(UFORE_UTLAND, actualRequest.journalfoerendeEnhet)

    }

    @Test
    fun `Ved kall til pensjoninformasjon der det returneres to saker saa skal vi velge den som har samme pesys sakid`() {
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()

        val sakInformasjon = listOf(
            SakInformasjon(
                sakId = "111111",
                sakType = SakType.UFOREP,
                sakStatus = SakStatus.LOPENDE,
                saksbehandlendeEnhetId = "",
                nyopprettet = false
            ), SakInformasjon(
                sakId = PESYS_SAKID,
                sakType = SakType.GJENLEV,
                sakStatus = SakStatus.OPPHOR,
                saksbehandlendeEnhetId = "",
                nyopprettet = false
            )
        )

        val identifisertPerson = identifisertPersonPDL(
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62),
                relasjon = GJENLEVENDE,
                saktype = null,
                sedType = P8000,
                fdato = LocalDate.of(1971, 6, 11),
                rinaDocumentId = "165sdugh587dfkgjhbkj"
            ),
            fnr = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62)
        )

        every { euxService.hentBuc(any()) } returns buc
        every { euxKlientLib.hentBucJson(any()) } returns sedJson
        every { euxKlientLib.hentSedJson(any(), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any(),) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any()) } returns LocalDate.of(1971, 6, 11)
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns sakInformasjon
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }
        every { gcpStorageService.hentFraGjenny(any()) } returns null
        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "EKSPEDERT",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any(), null) } returns opprettJournalPostResponse

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)
        val actualRequest = requestSlot.captured

        Assertions.assertEquals(UFORE_UTLAND, actualRequest.journalfoerendeEnhet)

    }

    fun identifisertPersonPDL(
        aktoerId: String = AKTOER_ID,
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPDLPerson =
        IdentifisertPDLPerson(aktoerId, landkode, geografiskTilknytning, personRelasjon, fnr, personNavn = personNavn)

}