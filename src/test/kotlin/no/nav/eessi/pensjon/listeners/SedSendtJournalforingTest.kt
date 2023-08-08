package no.nav.eessi.pensjon.listeners

import io.mockk.*
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.*
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase.Companion.FNR_OVER_60
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

private const val PESYS_SAKID = "25595454"
private const val RINA_ID = "148161"
internal class SedSendtJournalforingTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val norg2Klient = mockk<Norg2Klient>()
    private val norg2Service = Norg2Service(norg2Klient)
    private val oppgaveRoutingService = OppgaveRoutingService(norg2Service)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val euxKlient = mockk<EuxKlientLib>(relaxed = true)
    private val euxService = EuxService(euxKlient)
    private val bestemSakKlient = mockk<BestemSakKlient>(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)
    private val fagmodulKlient = mockk<FagmodulKlient>(relaxed = true)
    private val fagmodulService = FagmodulService(fagmodulKlient)
    private val journalpostKlient = mockk<JournalpostKlient>(relaxed = true)
    private val journalpostService = JournalpostService(journalpostKlient)
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxed = true)
    private val automatiseringStatistikkPublisher = mockk<AutomatiseringStatistikkPublisher>(relaxed = true)
    private val jouralforingService =
        JournalforingService(journalpostService, oppgaveRoutingService, mockk<PDFService>(relaxed = true).also {
            every { it.hentDokumenterOgVedlegg(any(), any(), any()) } returns Pair("1234568", emptyList())
        }, oppgaveHandler, mockk(), automatiseringStatistikkPublisher)

    private val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()
    private val sedListener = SedSendtListener(
        jouralforingService,
        personidentifiseringService,
        euxService,
        fagmodulService,
        bestemSakService,
        "test"
    )

    private val buc = Buc(
        id = PESYS_SAKID,
        documents = listOf(
            DocumentsItem(
                type = SedType.P8000,
                id = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1",
                status = "sent",
                direction = "OUT"
            )
        ), participants = listOf(
            Participant(
                role = "CaseOwner",
                organisation = Organisation(countryCode = "NO")
            )
        )
    )

    @BeforeEach
    fun setup() {
        sedListener.initMetrics()
        jouralforingService.initMetrics()
        euxService.initMetrics()
    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt med saktype GJENLEV fra pensjonsinformasjon returnerer sakInformasjo saktype GJENLEV og oppretter journalpost med automatisk journalforing 9999`() {
        val rinaId = RINA_ID
        val buc = Buc(
            id = rinaId,
            documents = listOf(
                DocumentsItem(
                    type = SedType.P8000,
                    id = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1",
                    status = "sent",
                    direction = "OUT"
                )
            ),
            participants = listOf(
                Participant(role = "CaseOwner", organisation = Organisation(countryCode = "NO"))
            )
        )
        val aktoerId = "3216549873212"
        val pesysSakId = PESYS_SAKID
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId = pesysSakId,
            sakType = SakType.GJENLEV,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        val identifisertPerson = identifisertPersonPDL(
            aktoerId = aktoerId,
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra("22117320034"),
                relasjon = Relasjon.GJENLEVENDE,
                saktype = null,
                sedType = SedType.P8000,
                fdato = LocalDate.of(1973, 11, 22),
                rinaDocumentId = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { euxKlient.hentBuc(eq(rinaId)) } returns buc
        every { euxKlient.hentSedJson(eq(rinaId), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any()) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973, 11, 22)
        every { fagmodulKlient.hentPensjonSaklist(eq(aktoerId)) } returns listOf(sakInformasjon)
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any()) } returns opprettJournalPostResponse

        val requestSlotOppgave = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(requestSlotOppgave)) }

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)

        val actualRequest = requestSlot.captured
        val actualRequestOppgave = requestSlotOppgave.captured

        Assertions.assertEquals(Enhet.AUTOMATISK_JOURNALFORING, actualRequest.journalfoerendeEnhet)
        Assertions.assertEquals(Enhet.AUTOMATISK_JOURNALFORING, actualRequestOppgave.tildeltEnhetsnr)
        Assertions.assertEquals(OppgaveType.JOURNALFORING, actualRequestOppgave.oppgaveType)

    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt ved feil saktype skal det opprettes oppgave`() {
        val rinaId = RINA_ID
        val aktoerId = "3216549873212"
        val pesysSakId = PESYS_SAKID
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId = pesysSakId,
            sakType = SakType.AFP_PRIVAT,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        val identifisertPerson = identifisertPersonPDL(
            aktoerId = aktoerId,
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra("22117320034"),
                relasjon = Relasjon.GJENLEVENDE,
                saktype = null,
                sedType = SedType.P8000,
                fdato = LocalDate.of(1973, 11, 22),
                rinaDocumentId = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { euxKlient.hentBuc(eq(rinaId)) } returns buc
        every { euxKlient.hentSedJson(eq(rinaId), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any()) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973, 11, 22)
        every { fagmodulKlient.hentPensjonSaklist(eq(aktoerId)) } returns listOf(sakInformasjon)
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any()) } returns opprettJournalPostResponse

        val requestSlotOppgave = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(requestSlotOppgave)) }

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)

        val actualRequest = requestSlot.captured
        val actualRequestOppgave = requestSlotOppgave.captured

        Assertions.assertEquals(Enhet.PENSJON_UTLAND, actualRequest.journalfoerendeEnhet)
        Assertions.assertEquals(Enhet.PENSJON_UTLAND, actualRequestOppgave.tildeltEnhetsnr)
        Assertions.assertEquals(OppgaveType.JOURNALFORING, actualRequestOppgave.oppgaveType)

    }

    @Test
    fun `Ved kall til pensjoninformasjon der det returneres to saker saa skal vi velge den som har samme pesys sakid`() {
        val aktoerId = "3216549873212"
        val pesysSakId = PESYS_SAKID
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
                sakId = pesysSakId,
                sakType = SakType.GJENLEV,
                sakStatus = SakStatus.OPPHOR,
                saksbehandlendeEnhetId = "",
                nyopprettet = false
            )
        )

        val identifisertPerson = identifisertPersonPDL(
            aktoerId = aktoerId,
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra("22117320034"),
                relasjon = Relasjon.GJENLEVENDE,
                saktype = null,
                sedType = SedType.P8000,
                fdato = LocalDate.of(1973, 11, 22),
                rinaDocumentId = "165sdugh587dfkgjhbkj"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { euxKlient.hentBuc(any()) } returns buc
        every { euxKlient.hentBucJson(any()) } returns sedJson
        every { euxKlient.hentSedJson(any(), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any()) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973, 11, 22)
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns sakInformasjon
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "EKSPEDERT",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any()) } returns opprettJournalPostResponse

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)
        val actualRequest = requestSlot.captured

        Assertions.assertEquals(Enhet.AUTOMATISK_JOURNALFORING, actualRequest.journalfoerendeEnhet)

    }

    fun identifisertPersonPDL(
        aktoerId: String = "3216549873215",
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPersonPDL =
        IdentifisertPersonPDL(aktoerId, landkode, geografiskTilknytning, personRelasjon, fnr, personNavn = personNavn)

}