package no.nav.eessi.pensjon.listeners

import io.mockk.*
import no.nav.eessi.pensjon.automatisering.AutomatiseringStatistikkPublisher
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
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
    private val oppgaveRoutingService = mockk<OppgaveRoutingService>()
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val sedDokumentHelper = mockk<EuxService>(relaxed = true)
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
        sedDokumentHelper,
        fagmodulService,
        bestemSakService,
        "test")

    @BeforeEach
    fun setup() {
        sedListener.initMetrics()
        jouralforingService.initMetrics()
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
    fun `Ved kall til pensjonSakInformasjonSendt der vi har saktype GJENLEV i svar fra pensjonsinformasjon returneres sakInformasjon med saktype GJENLEV`() {
        val rinaId = "148161"
        val aktoerId = "3216549873212"
        val pesysSakId = "25595454"
        val buc = Buc(id = "321654687")
        val sed = SED(SedType.P8000, nav = Nav(eessisak = listOf(EessisakItem(saksnummer = pesysSakId, land = "NO"))))
        val forenkletSed = ForenkletSED(rinaId, SedType.P8000, SedStatus.SENT)
        val sedHendelse = javaClass.getResource("/eux/hendelser/P_BUC_05_P8000_02.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId  = pesysSakId,
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
                fdato = LocalDate.of(1973,11,22 ),
                rinaDocumentId = "165sdugh587dfkgjhbkj"
            ),
            fnr = Fodselsnummer.fra("22117320034")
        )

        every { sedDokumentHelper.hentBuc(any()) } returns buc
        every { sedDokumentHelper.isNavCaseOwner(any()) } returns true
        every { sedDokumentHelper.hentAlleGyldigeDokumenter(any()) } returns listOf(forenkletSed)
        every { sedDokumentHelper.hentAlleSedIBuc(rinaId, any()) } returns listOf(Pair(rinaId, sed))
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { sedDokumentHelper.hentAlleKansellerteSedIBuc(rinaId, any()) } returns emptyList()
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any() ) } returns identifisertPerson
        every { personidentifiseringService.hentFodselsDato(any(), any(), any()) } returns LocalDate.of(1973,11,22)
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns listOf(sakInformasjon)
        every { oppgaveRoutingService.hentEnhet(any()) } returns Enhet.PENSJON_UTLAND

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any()) } returns opprettJournalPostResponse

        sedListener.consumeSedSendt(sedHendelse, cr, acknowledgment)
        val actualRequest = requestSlot.captured

        assertEquals(Enhet.PENSJON_UTLAND, actualRequest.journalfoerendeEnhet)

    }

//    @Test
//    fun `Ved kall til pensjonSakInformasjonSendt der vi har saktype BARNEP i svar fra bestemSak returneres sakInformasjon med saktype BARNEP`() {
//        val aktoerId = "1234567891234"
//        val identifisertPerson = identifisertPersonPDL(
//            landkode = "NO",
//            geografiskTilknytning = null,
//            personRelasjon = null,
//            fnr = Fodselsnummer.fra("16115038745")
//        )
//
//        val sakInformasjon = SakInformasjon(
//            sakId  = "25595454",
//            sakType = SakType.BARNEP,
//            sakStatus = SakStatus.LOPENDE,
//            saksbehandlendeEnhetId = "",
//            nyopprettet = false
//        )
//
//        every { fagmodulKlient.hentPensjonSaklist(aktoerId) } returns emptyList()
//        every { bestemSakKlient.kallBestemSak(any()) } returns BestemSakResponse(sakInformasjonListe = listOf(sakInformasjon))
//
//        val actual = sedListener.pensjonSakInformasjonSendt(identifisertPerson, P_BUC_05, null, emptyList() )
//
//        assertEquals(null, actual?.sakType)
//    }

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
