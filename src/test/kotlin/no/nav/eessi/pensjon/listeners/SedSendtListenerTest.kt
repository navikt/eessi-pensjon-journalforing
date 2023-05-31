package no.nav.eessi.pensjon.listeners

import io.mockk.*
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.SedType.P8000
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.OPPHOR
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
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
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

internal class SedSendtListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val norg2Klient = mockk<Norg2Klient>()
    private val norg2Service = Norg2Service(norg2Klient)
    private val oppgaveRoutingService = OppgaveRoutingService(norg2Service)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val euxKlient = mockk<EuxKlientLib>()
    private val euxService = EuxService(euxKlient)
    private val bestemSakKlient = mockk<BestemSakKlient>(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)
    private val fagmodulKlient = mockk<FagmodulKlient>(relaxed = true)
    private val fagmodulService = FagmodulService(fagmodulKlient)
    private val journalpostKlient = mockk<JournalpostKlient>()
    private val journalpostService = JournalpostService(journalpostKlient)
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxed = true)
    private val automatiseringStatistikkPublisher = mockk<AutomatiseringStatistikkPublisher>(relaxed = true)
    private val jouralforingService = JournalforingService(journalpostService, oppgaveRoutingService, mockk<PDFService>(relaxed = true).also {
          every { it.hentDokumenterOgVedlegg(any(), any(),any()) } returns Pair("1234568", emptyList())
    }, oppgaveHandler, mockk(), automatiseringStatistikkPublisher)

    private val sedListener = SedSendtListener(jouralforingService,
        personidentifiseringService,
        euxService,
        fagmodulService,
        bestemSakService,
        "test")

    @BeforeEach
    fun setup() {
        sedListener.initMetrics()
        jouralforingService.initMetrics()
        euxService.initMetrics()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedSendt hendelse konsumeres, skal melding ackes`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en exception ved sedSendt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertThrows<RuntimeException> {
            sedListener.consumeSedSendt("Explode!", cr, acknowledgment)
        }
        verify { acknowledgment wasNot Called }
    }

    @Test
    fun `Mottat og sendt Sed med ugyldige verdier kaster exception`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/BAD_BUC_01.json")))

        assertThrows<SedSendtRuntimeException> {
            sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        }
    }

    @Test
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
        verify { jouralforingService wasNot Called }
    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt med saktype GJENLEV fra pensjonsinformasjon returnerer sakInformasjo saktype GJENLEV og oppretter journalpost med automatisk journalforing 9999`() {
        val rinaId = "148161"
        val buc = Buc(
            id = rinaId,
            documents = listOf(DocumentsItem(
                type = P8000,
                id = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1",
                status = "sent",
                direction = "OUT"
            )),
            participants = listOf(
                Participant(role = "CaseOwner", organisation = Organisation(countryCode = "NO"))
            )
        )
        val aktoerId = "3216549873212"
        val pesysSakId = "25595454"
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId  = pesysSakId,
            sakType = GJENLEV,
            sakStatus = LOPENDE,
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
                sedType = P8000,
                fdato = LocalDate.of(1973,11,22 ),
                rinaDocumentId = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { euxKlient.hentBuc(eq(rinaId)) } returns buc
        every { euxKlient.hentSedJson(eq(rinaId), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any() ) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973,11,22)
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

        assertEquals(AUTOMATISK_JOURNALFORING, actualRequest.journalfoerendeEnhet)
        assertEquals(PENSJON_UTLAND, actualRequestOppgave.tildeltEnhetsnr)
        assertEquals(OppgaveType.JOURNALFORING, actualRequestOppgave.oppgaveType)

    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt ved feil saktype skal det opprettes oppgave`() {
        val rinaId = "148161"
        val buc = Buc(
            id = rinaId,
            documents = listOf(DocumentsItem(
                type = P8000,
                id = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1",
                status = "sent",
                direction = "OUT"
            )),
            participants = listOf(
                Participant(organisation = Organisation(countryCode = "NO"))
            )
        )
        val aktoerId = "3216549873212"
        val pesysSakId = "25595454"
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()
        val sedJson = javaClass.getResource("/sed/P_BUC_05-P8000.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId  = pesysSakId,
            sakType = AFP_PRIVAT,
            sakStatus = LOPENDE,
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
                sedType = P8000,
                fdato = LocalDate.of(1973,11,22 ),
                rinaDocumentId = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { euxKlient.hentBuc(eq(rinaId)) } returns buc
        every { euxKlient.hentSedJson(eq(rinaId), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any() ) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973,11,22)
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

        assertEquals(ID_OG_FORDELING, actualRequest.journalfoerendeEnhet)
        assertEquals(ID_OG_FORDELING, actualRequestOppgave.tildeltEnhetsnr)
        assertEquals(OppgaveType.JOURNALFORING, actualRequestOppgave.oppgaveType)

    }

    @Test
    fun `Ved kall til pensjoninformasjon der det returneres to saker og GJENLEV saktypen er LOPENDE saa skal det opprettes journalpost med saktype GJENLEV med enhet 0001`() {
        val rinaId = "148161"
        val aktoerId = "3216549873212"
        val pesysSakId = "25595454"
        val buc = Buc(id = "321654687")
        val sed = SED(P8000, nav = Nav(eessisak = listOf(EessisakItem(saksnummer = pesysSakId, land = "NO"))))
        val forenkletSed = ForenkletSED(rinaId, P8000, SedStatus.SENT)
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()

        val sakInformasjon = listOf(SakInformasjon(
                sakId = "111111",
                sakType =  UFOREP,
                sakStatus = LOPENDE,
                saksbehandlendeEnhetId = "",
                nyopprettet = false
        ), SakInformasjon(
                sakId =  "222222",
                sakType = GJENLEV,
                sakStatus =  OPPHOR,
                saksbehandlendeEnhetId =  "",
                nyopprettet =  false
        ))

        val identifisertPerson = identifisertPersonPDL(
            aktoerId = aktoerId,
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra("22117320034"),
                relasjon = Relasjon.GJENLEVENDE,
                saktype = null,
                sedType = P8000,
                fdato = LocalDate.of(1973,11,22 ),
                rinaDocumentId = "165sdugh587dfkgjhbkj"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { euxService.hentBuc(any()) } returns buc
        every { euxService.isNavCaseOwner(any()) } returns true
        every { euxService.hentAlleGyldigeDokumenter(any()) } returns listOf(forenkletSed)
        every { euxService.hentAlleSedIBuc(rinaId, any()) } returns listOf(Pair(rinaId, sed))
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { euxService.hentAlleKansellerteSedIBuc(rinaId, any()) } returns emptyList()
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any() ) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973,11,22)
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns sakInformasjon

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

        assertEquals(PENSJON_UTLAND, actualRequest.journalfoerendeEnhet)

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
