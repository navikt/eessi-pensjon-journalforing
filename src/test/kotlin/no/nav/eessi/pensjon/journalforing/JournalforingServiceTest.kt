package no.nav.eessi.pensjon.journalforing

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P2100
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Service
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsHandler
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.kafka.core.KafkaTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"

internal class JournalforingServiceTest {

    //TODO: SE OVER DENNE TESTEN OG SE OM DET ER MULIGHET FOR FORBERING

    private val journalpostKlient = mockk<JournalpostKlient>()
    private val journalpostService = JournalpostService(journalpostKlient)
    private val pdfService = mockk<PDFService>()
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxUnitFun = true)
    private val kravHandeler = mockk<KravInitialiseringsHandler>()
    private val gcpStorageService = mockk<GcpStorageService>(relaxed = true)
    private val kravService = KravInitialiseringsService(kravHandeler)

    private val norg2Service = mockk<Norg2Service> {
        every { hentArbeidsfordelingEnhet(any()) } returns null
    }
    protected val automatiseringHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val statistikkPublisher = StatistikkPublisher(automatiseringHandlerKafka)
    private val oppgaveRoutingService = OppgaveRoutingService(norg2Service)

    private val journalforingService = JournalforingService(
            journalpostService,
            oppgaveRoutingService,
            pdfService,
            oppgaveHandler,
            kravService,
            gcpStorageService,
            statistikkPublisher
    )

    private val fdato = LocalDate.now()
    private val opprettJournalpostRequestCapturingSlot = slot<OpprettJournalpostRequest>()
    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
        private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
        private val STERK_BUSK = Fodselsnummer.fra("12011577847")!!
    }

    @BeforeEach
    fun setup() {
        journalforingService.nameSpace = "test"

        //MOCK RESPONSES
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2000) } returns Pair("P2000 Supported Documents", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2100) } returns Pair("P2100 Krav om etterlattepensjon", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P2200) } returns Pair("P2200 Supported Documents", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.R004) } returns Pair("R004 - Melding om utbetaling", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.R005) } returns Pair("R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)", emptyList())
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), SedType.P15000) } returns Pair("P15000 - Overføring av pensjonssaker til EESSI (foreløpig eller endelig)", emptyList())

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "EKSPEDERT",
            melding = "",
            journalpostferdigstilt = false,
        )

        every { journalpostKlient.opprettJournalpost(capture(opprettJournalpostRequestCapturingSlot), any(), null) } returns opprettJournalPostResponse
    }

    @Test
    fun `Sendt sed P2200 med ukjent fnr skal sette status avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(AKTOERID, SEDPersonRelasjon(null, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID), "NOR")

        justRun { journalpostKlient.settStatusAvbrutt(eq("12345")) }

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2200),
            identifisertePersoner = 0,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)

        verify { journalpostKlient.settStatusAvbrutt(journalpostId = "12345") }
    }

    @Test
    fun `Sendt P2000 med ukjent fnr der SED inneholder pesys sakId saa skal ikke status settes til avbrutt og journalpost samt JFR oppgave opprettes`() {
        val sedHendelse = mockedSedHendelse(P_BUC_01, SedType.P2000)

        val oppgaveSlot = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(oppgaveSlot)) }

        journalforingService.journalforUkjentPersonKjentPersysSakId(
            sedHendelse,
            SENDT,
            null,
            null,
            "123456"
        )

        verify(exactly = 0) { journalpostService.settStatusAvbrutt(any()) }
//        verify(exactly = 1) { journalpostService.opprettJournalpost(
//            any(),
//            any(),
//            any(),
//            any(),
//            any(),
//            any(),
//            any(),
//            any(),
//            any(),
//            any(),
//            any()
//        ) }
        assertEquals(OppgaveType.JOURNALFORING, oppgaveSlot.captured.oppgaveType)
    }

    @Test
    fun `Sendt sed P2000 med ukjent fnr SED inneholder IKKE pesys saksId saa skal ikke status settes til avbrutt og journalpost opprettes`() {
        val sedHendelse = mockedSedHendelse(P_BUC_01, SedType.P2000)

        justRun { journalpostKlient.settStatusAvbrutt(any()) }

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            null,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2000),
            identifisertePersoner = 0,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        verify(atLeast = 1) { journalpostKlient.settStatusAvbrutt(any()) }
        verify(atLeast = 1) { journalpostKlient.opprettJournalpost(any(), any(), any()) }
        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
    }

    @Test
    fun `Sendt sed P2200 med ukjent fnr med saksinfo der sakid er null så skal status settes til avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        justRun { journalpostKlient.settStatusAvbrutt(any()) }
        val sakInformasjonMock = mockk<SakInformasjon>().apply {
            every { sakId } returns null
            every { sakType } returns ALDER
        }
        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            null,
            LEALAUS_KAKE.getBirthDate(),
            null,
            sakInformasjonMock,
            SED(type = SedType.P2200),
            identifisertePersoner = 0,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        verify(exactly = 1) { journalpostService.settStatusAvbrutt(any()) }
    }

    @Test
    fun `Mottatt sed P2200 med ukjent fnr skal ikke sette status avbrutt`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2200),
            identifisertePersoner = 0,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        verify(exactly = 0) { journalpostService.settStatusAvbrutt(journalpostId = "123") }

    }

    @Test
    fun `Utgaaende sed P2200 med ukjent fnr skal sette status avbrutt og opprette behandle-sed oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )
        justRun { journalpostKlient.settStatusAvbrutt(eq("12345"))}

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2200),
            identifisertePersoner = 0,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)

        verify(exactly = 0) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any())}

    }

    @Test
    fun `Utgaaende sed P2200 med kjent fnr skal status ikke settes til avbrutt og vi skal opprette journalfoerings oppgave`() {
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P2100_b12e06dda2c7474b9998c7139c841646_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "147730",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "b12e06dda2c7474b9998c7139c841646",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "22117320034"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(Fodselsnummer.fra("22117320034"), Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )

        //val journalPostResponse = OpprettJournalPostResponse("","","",false)

        //every { journalpostService.opprettJournalpost(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns journalPostResponse
        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LocalDate.of(1973,11,22),
            null,
            null,
            SED(type = SedType.P2200),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(NFP_UTLAND_AALESUND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)

        verify(exactly = 1) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any())}

    }

    @Test
    fun `Sendt gyldig Sed R004 på R_BUC_02`() {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json").readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            ALDER,
            null,
            SED(type = SedType.R004),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(OKONOMI_PENSJON, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @ParameterizedTest
    @EnumSource(
        BucType::class, names = [
            "R_BUC_02"
        ]
    )
    fun `Buc av denne typen skal ikke journalfores med avbrutt`(bucType: BucType) {
        val hendelse = javaClass.getResource("/eux/hendelser/R_BUC_02_R004.json").readText()
        val sedHendelse = SedHendelse.fromJson(hendelse).copy(bucType = bucType)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(null, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        every { gcpStorageService.eksisterer(any()) } returns false

        val mox = journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            ALDER,
            null,
            SED(type = SedType.R004),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            gjennySakId = null
        )

        println(mox)

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "R004",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "4303",
              "aktoerId" : "12078945602",
              "rinaSakId" : "2536475861",
              "hendelseType" : "SENDT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "PEN"
              }""".trimIndent()
        )
        verify { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }
    }

    @Test
    fun `Ved mottatt P2000 som kan automatisk ferdigstilles så skal det opprettes en Behandle SED oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_SE.json")!!.readText()
        val sed = mapJsonToAny<P2000>(javaClass.getResource("/sed/P2000-NAV.json")!!.readText())
        val sedHendelse = SedHendelse.fromJson(hendelse).copy(bucType = P_BUC_01)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            ALDER,
            null,
            sed,
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "P2000",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "PEN"
              }""".trimIndent()
        )
        verify { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }
    }

    @Test
    fun `Ved mottatt P2100 som kan automatisk ferdigstilles så skal det opprettes en Behandle SED oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_02_P2100_SE.json")!!.readText()
        val sed = mapJsonToAny<P2100>(javaClass.getResource("/sed/P2100.json")!!.readText())
        val sedHendelse = SedHendelse.fromJson(hendelse).copy(bucType = P_BUC_02)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }
        justRun { journalpostKlient.settStatusAvbrutt(eq("12345")) }
        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            GJENLEV,
            null,
            sed,
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "P2100",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "PEN"
              }""".trimIndent()
        )
        verify(exactly = 1) { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }
    }

    @ParameterizedTest
    @EnumSource(
        SedType::class, names = [
            "X001", "X002", "X003", "X004", "X005", "X006", "X007", "X008", "X009", "X010",
            "X013", "X050", "H001", "H002", "H020", "H021", "H070", "H120", "H121"
        ]
    )
    fun `Sed av denne typen skal ikke journalfores med avbrutt`(sedType: SedType) {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json")?.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse!!).copy(sedType = sedType)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(null, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        every { pdfService.hentDokumenterOgVedlegg(any(), any(), sedType) } returns Pair("$sedType supported Documents", emptyList())

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            ALDER,
            null,
            SED(type = sedType),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        verify (exactly = 0){ journalpostService.settStatusAvbrutt(any()) }
        val oppgaveMelding = mapJsonToAny<OppgaveMelding>("""{
              "sedType" : "$sedType",
              "journalpostId" : "12345",
              "tildeltEnhetsnr" : "4303",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "SENDT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING"}""".trimIndent()
        )
        verify { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(eq(oppgaveMelding)) }

    }

    @Test
    fun `Sendt gyldig Sed R005 på R_BUC_02`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "SE",
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            UFOREP,
            null,
            SED(type = SedType.R005),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer SENDT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "SE"
        )
        identifisertPerson.personListe = listOf(identifisertPerson, identifisertPerson)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            UFOREP,
            null,
            SED(type = SedType.R005),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en R_BUC_02 og sed med flere personer MOTTATT Så skal det opprettes Oppgave og enhet 4303`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            geografiskTilknytning = "3811"
        )
        val dodPerson = identifisertPersonPDL(
            "22078945602",
            sedPersonRelasjon(),
            "NO",
            "3811",
            personNavn = "Dod Begravet",
        )

        identifisertPerson.personListe = listOf(identifisertPerson, dodPerson)

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            fdato,
            ALDER,
            null,
            SED(type = SedType.R005),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }


    @Test
    fun `Sendt gyldig Sed P2000`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse, SENDT, identifisertPerson, LEALAUS_KAKE.getBirthDate(), null, null, SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Sendt gyldig Sed P2000 med UK saa skal landkode konverteres til GB fordi Pesys kun godtar GB`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_med_UK.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse, SENDT, identifisertPerson, LEALAUS_KAKE.getBirthDate(), null, null, SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Sendt gyldig Sed P2200`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID),
            "NOR"
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2200),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(UFORE_UTLANDSTILSNITT, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Sendt Sed i P_BUC_10`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P15000.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P15000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat gyldig Sed P2000`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt en SED med ugyldig fnr i SED så søk etter fnr i andre SEDer i samme buc`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE)
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat gyldig Sed P2100`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "NOR"
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            ALDER,
            null,
            SED(type = SedType.P2100), identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(NFP_UTLAND_AALESUND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat gyldig Sed P2200`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_03_P2200.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(SLAPP_SKILPADDE)
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            SLAPP_SKILPADDE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P2200),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(UFORE_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.UFOREPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Mottat Sed i P_BUC_10`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_10_P15000.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            null,
            SED(type = SedType.P15000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.ALDERSPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som SAKSEIER så skal SEDen journalføres maskinelt`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )
        val sakInformasjon = SakInformasjon("111111", GJENLEV, LOPENDE, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            GJENLEV,
            sakInformasjon,
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at saksbhandler oppretter en P2100 med NORGE som DELTAKER så skal SEDen journalføres maskinelt på gjenlevnde`() {

        val avdodFnr = "02116921297"
        val hendelse = """
            {
              "id": 27403,
              "sedId": "P2100_e6b0d1bfede5443face4059e720e9d43_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "1033470",
              "avsenderId": "NO:NAVAT07",
              "avsenderNavn": "NAV ACCEPTANCE TEST 07",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVAT08",
              "mottakerNavn": "Vilniaus",
              "mottakerLand": "LI",
              "rinaDokumentId": "e6b0d1bfede5443face4059e720e9d43",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "$avdodFnr"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertGjenlevendePerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.GJENLEVENDE, GJENLEV, rinaDocumentId = RINADOK_ID),
            "Test Testesen",
            "",
            null
        )
        val saksInfo = SakInformasjon("111111", GJENLEV, LOPENDE, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertGjenlevendePerson,
            LEALAUS_KAKE.getBirthDate(),
            GJENLEV,
            saksInfo,
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(PENSJON_UTLAND, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
        //legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 med et norsk fnr eller dnr med UFØREP og sakstatus er Avsluttet så skal SED journalføres med oppgave ikke ferdigstille`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon()
        )
        val sakInformasjon = SakInformasjon("111222", UFOREP, AVSLUTTET, "4303", false)

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            null,
            sakInformasjon,
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
        // TODO: legg inn sjekk på at seden ligger i Joark på riktig bruker, dvs søker og ikke den avdøde

    }

    @Test
    fun `Gitt at saksbehandler har opprettet en p2100 med mangelfullt fnr eller dnr så skal det opprettes en journalføringsoppgave og settes til enhet 4303`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            "",
            sedPersonRelasjon(),
            "NO"
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            null,
            null,
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at saksbehandler har opprettet en P2100 og bestemsak returnerer ALDER og UFOREP som sakstyper så skal det opprettes en journalføringsoppgave og enhet setttes til 4303 NAV Id og fordeling`() {

        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_02_P2100.json")))
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(),
            "NO"
        )

        journalforingService.journalfor(
            sedHendelse,
            SENDT,
            identifisertPerson,
            fdato,
            null,
            null,
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        assertEquals(ID_OG_FORDELING, opprettJournalpostRequestCapturingSlot.captured.journalfoerendeEnhet)
        assertEquals(Behandlingstema.GJENLEVENDEPENSJON, opprettJournalpostRequestCapturingSlot.captured.behandlingstema)
    }

    @Test
    fun `Gitt at vi mottar en P_BUC_02 med kjent aktørid Når det finnes kun en sakstype fra bestemsak`() {

        val avdodFnr = "02116921297"
        val hendelse = """
            {
              "id": 27403,
              "sedId": "P2100_e6b0d1bfede5443face4059e720e9d43_2",
              "sektorKode": "P",
              "bucType": "P_BUC_02",
              "rinaSakId": "1033470",
              "avsenderId": "NO:NAVAT07",
              "avsenderNavn": "POLEN",
              "avsenderLand": "PL",
              "mottakerId": "NO:NAVAT08",
              "mottakerNavn": "Oslo",
              "mottakerLand": "NO",
              "rinaDokumentId": "e6b0d1bfede5443face4059e720e9d43",
              "rinaDokumentVersjon": "2",
              "sedType": "P2100",
              "navBruker": "$avdodFnr"
            }
        """.trimIndent()

        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertGjenlevendePerson = identifisertPersonPDL(
            AKTOERID,
            SEDPersonRelasjon(STERK_BUSK, Relasjon.GJENLEVENDE, BARNEP, rinaDocumentId = RINADOK_ID)
        )
        val saksInfo = SakInformasjon("111111", BARNEP, LOPENDE, "4862", false)

        journalforingService.journalfor(
            sedHendelse,
            MOTTATT,
            identifisertGjenlevendePerson,
            STERK_BUSK.getBirthDate(),
            BARNEP,
            saksInfo,
            SED(type = SedType.P2100),
            identifisertePersoner = 2,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )

        verify(atLeast = 1) {
            journalpostKlient.opprettJournalpost(any(), any(), any())
        }
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype BARNEP så skal det settes teama PEN`() {
        val result = journalforingService.hentTema(P_BUC_02, BARNEP, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype UFOREP så skal det settes teama UFO`() {
        val result = journalforingService.hentTema(P_BUC_02, UFOREP, LEALAUS_KAKE, 1, RINADOK_ID)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en P_BUC_02 med saktype GJENLEVENDE så skal det settes teama PEN`() {
        val result = journalforingService.hentTema(P_BUC_02, GJENLEV, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_01 med saktype ALDER så skal det settes teama PEN`() {
        val result = journalforingService.hentTema(P_BUC_01, null, LEALAUS_KAKE, 2, RINADOK_ID)
        val result2 = journalforingService.hentTema(P_BUC_01, null, LEALAUS_KAKE, 1, RINADOK_ID)
        assertEquals(Tema.PENSJON, result)
        assertEquals(Tema.PENSJON, result2)
    }

    @Test
    fun `gitt det er en R_BUC_02 og sed er R004 og enhet er 4819 så skal det settes teama PEN`() {
        val result = journalforingService.hentTema(BucType.R_BUC_02, ALDER, LEALAUS_KAKE, 1, RINADOK_ID)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er UFOREP så skal det settes teama UFO`() {
        val result = journalforingService.hentTema(BucType.R_BUC_02, UFOREP, LEALAUS_KAKE, 1, RINADOK_ID)
        assertEquals(Tema.UFORETRYGD, result)
    }

    @Test
    fun `gitt det er en R_BUC_02 ytelseype er ALDER så skal det settes teama PEN`() {
        val result = journalforingService.hentTema(BucType.R_BUC_02, ALDER, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, result)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype IKKE er UFOREP så skal det settes teama PEN`() {
        val resultatGENRL = journalforingService.hentTema(BucType.P_BUC_05, GENRL, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, resultatGENRL)

        val resultatOMSORG = journalforingService.hentTema(BucType.P_BUC_05, OMSORG, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, resultatOMSORG)

        val resultatALDER = journalforingService.hentTema(BucType.P_BUC_05, ALDER, fnr = SLAPP_SKILPADDE, 1, RINADOK_ID)
        assertEquals(Tema.PENSJON, resultatALDER)

        val resultatGJENLEV = journalforingService.hentTema(BucType.P_BUC_05, GJENLEV, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, resultatGJENLEV)

        val resultatBARNEP = journalforingService.hentTema(BucType.P_BUC_05, BARNEP, LEALAUS_KAKE, 2, RINADOK_ID)
        assertEquals(Tema.PENSJON, resultatBARNEP)
    }

    @Test
    fun `gitt det er en P_BUC_05 ytelseype er UFOREP så skal det settes teama UFO`() {
        val result = journalforingService.hentTema(BucType.P_BUC_05, UFOREP, LEALAUS_KAKE,  1, RINADOK_ID)
        assertEquals(Tema.UFORETRYGD, result)
    }

    private fun saksbehandlerInfo(): Pair<String, Enhet?>? = null

    fun mockedSedHendelse(buc: BucType, sed: SedType) : SedHendelse{
        return mockk<SedHendelse>(relaxed = true).apply {
            every { bucType } returns buc
            every { sedType } returns sed
        }
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

    private fun navAnsattInfo(): Pair<String, Enhet?>? = null


}
