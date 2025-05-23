package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.EuxCacheableKlient
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Service
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteResponseData
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.journalforing.etterlatte.GjennySakType
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.krav.BehandleHendelseModel
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsHandler
import no.nav.eessi.pensjon.journalforing.krav.KravInitialiseringsService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OpprettOppgaveService
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.listeners.SedSendtListener
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.navansatt.NavansattKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakResponse
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedested
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal open class JournalforingTestBase {

    companion object {
        const val SAK_ID = "12345678"

        const val FNR_OVER_62 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN_UNDER_62 = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"

        fun mockHentGjennySakMedError(): Result<EtterlatteResponseData?> {
            return mockHentGjennySak(null)
        }
        fun mockHentGjennySak(
            sakId: String?,
            id: Int = 1,
            ident: String? = null,
            enhet: String? = null,
            sakType: GjennySakType? = null
        ): Result<EtterlatteResponseData?> {
            return when (sakId) {
                "validId" -> {
                    val mockData = EtterlatteResponseData(
                        id = id,
                        ident = ident,
                        enhet = enhet,
                        sakType = sakType
                    )
                    Result.success(mockData)
                }
                "notFoundId" -> {
                    Result.failure(NoSuchElementException("Sak ikke funnet (404) for sakId: $sakId"))
                }
                "errorId" -> {
                    Result.failure(IllegalStateException("Uventet statuskode: 500 for sakId: $sakId"))
                }
                else -> {
                    Result.failure(Exception("En ukjent feil oppstod for sakId: $sakId"))
                }
            }
        }
    }

    protected val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    protected val euxKlient: EuxCacheableKlient = EuxCacheableKlient(mockk())
    protected val navansattKlient: NavansattKlient = mockk(relaxed = true)
    {
        every { navAnsattMedEnhetsInfo(any(), any()) } returns null
    }
    private val euxService = EuxService(euxKlient)
    private val fagmodulService = FagmodulService(fagmodulKlient)

    protected val norg2Service: Norg2Service = mockk(relaxed = true)
    protected val journalpostKlient: JournalpostKlient = mockk(relaxed = true, relaxUnitFun = true)

    private val pdfService: PDFService = PDFService(euxService)

    val oppgaveRoutingService: OppgaveRoutingService = OppgaveRoutingService(norg2Service)

    val etterlatteService = mockk<EtterlatteService>(relaxed = true)

    protected val oppgaveHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    protected val kravInitHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val oppgaveHandler: OppgaveHandler = OppgaveHandler(oppgaveHandlerKafka)
    private var opprettOppgaveService: OpprettOppgaveService = OpprettOppgaveService(oppgaveHandler)
    val journalpostService = spyk(JournalpostService(journalpostKlient, pdfService, opprettOppgaveService))

    private val kravHandler = KravInitialiseringsHandler(kravInitHandlerKafka)
    private val kravService = KravInitialiseringsService(kravHandler)
    protected val automatiseringHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }
    private val statistikkPublisher = StatistikkPublisher(automatiseringHandlerKafka)

    protected val gcpStorageService : GcpStorageService = mockk<GcpStorageService>()

    @BeforeEach
    fun setup() {
        ReflectionTestUtils.setField(kravHandler, "kravTopic", "kravTopic")
        journalforingService.nameSpace = "test"
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { gcpStorageService.hentFraGjenny(any()) } returns null
    }

    val hentSakService = HentSakService(etterlatteService, gcpStorageService)
    val hentTemaService = HentTemaService(journalpostService, gcpStorageService)
    val journalforingService: JournalforingService = JournalforingService(
        journalpostService = journalpostService,
        oppgaveRoutingService = oppgaveRoutingService,
        kravInitialiseringsService = kravService,
        statistikkPublisher = statistikkPublisher,
        gcpStorageService = gcpStorageService,
        hentSakService = hentSakService,
        hentTemaService = hentTemaService,
        oppgaveService = opprettOppgaveService,
        env = null,
    )

    protected val personService: PersonService = mockk(relaxed = true)
    private val personSok = PersonSok(personService)
    val personidentifiseringService = PersonidentifiseringService(personSok, personService)


    protected val bestemSakKlient: BestemSakKlient = mockk(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)

    protected val mottattListener: SedMottattListener = SedMottattListener(
        journalforingService = journalforingService,
        personidentifiseringService = personidentifiseringService,
        euxService = euxService,
        fagmodulService = fagmodulService,
        bestemSakService = bestemSakService,
        gcpStorageService = gcpStorageService,
        profile = "test"
    )
    protected val sendtListener: SedSendtListener = SedSendtListener(
        journalforingService = journalforingService,
        personidentifiseringService = personidentifiseringService,
        euxService = euxService,
        bestemSakService = bestemSakService,
        fagmodulService = fagmodulService,
        gcpStorageService = gcpStorageService,
        profile = "test",
        navansattKlient = navansattKlient
    )

    @AfterEach
    fun after() {
        clearAllMocks()
    }

    /**
     * TestRunner for testing av Journalføring med to personer.
     *
     * @param fnr: Hovedperson/forsikrede sitt fnr.
     * @param fnrAnnenPerson: Annen person sitt fnr.
     * @param saker: En liste med saker som skal returneres på søkeren sin aktørId. (Default = emptyList())
     * @param sakId: SakID tilknyttet bestemt sak som skal uthentes. (Default = [SAK_ID])
     * @param land: Landet personene tilhører. Kan kun spesifisere én som brukes på alle. (Default = "NOR")
     * @param rolle: Rollen tilknyttet [fnrAnnenPerson]. Kan være "01", "02", eller "03".
     * @param assertBlock: En [Unit] for å kjøre assertions/validering på [OpprettJournalpostRequest]
     */
    protected fun testRunnerFlerePersoner(
        fnr: String?,
        fnrAnnenPerson: String?,
        saker: List<SakInformasjon> = emptyList(),
        sakId: String? = SAK_ID,
        harAdressebeskyttelse: Boolean = false,
        land: String = "NOR",
        rolle: Rolle?,
        hendelseType: HendelseType = SENDT,
        fDatoFraAnnenPerson: String? = "1988-07-12",
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, fnr, createAnnenPerson(fnr = fnrAnnenPerson, rolle = rolle), sakId, fdato = fDatoFraAnnenPerson))
        initCommonMocks(sed)

        every { personService.harAdressebeskyttelse(any()) } returns harAdressebeskyttelse
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { gcpStorageService.hentFraGjenny(any()) } returns null

        sakId?.let {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(it)
        } ?: run {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()
        }

        if (fnr != null) {
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(
                fnr,
                "Mamma forsørger",
                "Etternavn",
                land,
                aktorId = AKTOER_ID
            )
        }

        if (fnrAnnenPerson != null) {
            every { personService.hentPerson(NorskIdent(fnrAnnenPerson)) } returns createBrukerWith(
                fnrAnnenPerson,
                "Barn",
                "Diskret",
                land,
                "1213",
                aktorId = AKTOER_ID_2
            )
        }

        if (rolle == Rolle.ETTERLATTE)
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns saker
        else
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000,  rinaDokumentId = "44cb68f89a2f4e748934fb4722721018")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val journalpostRequest = slot<OpprettJournalpostRequest>()

        if (hendelseType == SENDT)
            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else {
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            // forvent tema == PEN og enhet 2103
            assertEquals(hendelseType, mapJsonToAny<OppgaveMelding>(meldingSlot.captured).hendelseType)
        }

        createMockedJournalPostWithOppgave(journalpostRequest, hendelse, hendelseType)

        val request = journalpost.captured

        assertBlock(request)

        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }

        if (hendelseType == SENDT) {
            assertEquals(JournalpostType.UTGAAENDE, request.journalpostType)

            val antallPersoner = listOfNotNull(fnr, fnrAnnenPerson).size
            val antallKallTilPensjonSaklist = if (antallPersoner > 0) 1 else 0
            verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }
        } else {
            assertEquals(JournalpostType.INNGAAENDE, request.journalpostType)

            //verify(exactly = 0) { fagmodulKlient.hentPensjonSaklist(any()) }
        }

        clearAllMocks()
    }

    fun createMockedJournalPostWithOppgave(
        journalpostRequest: CapturingSlot<OpprettJournalpostRequest>,
        hendelse: String,
        hendelseType: HendelseType
    ) {
        if (journalpostRequest.isCaptured && journalpostRequest.captured.bruker == null) {
            val lagretJournalpost = JournalpostMedSedInfo(
                journalpostRequest.captured,
                mapJsonToAny<SedHendelse>(hendelse),
                hendelseType
            )
            val response  = journalpostService.sendJournalPost(lagretJournalpost.journalpostRequest, lagretJournalpost.sedHendelse, lagretJournalpost.sedHendelseType, null)

            journalforingService.vurderSettAvbruttOgLagOppgave(
                Fodselsnummer.fra(lagretJournalpost.journalpostRequest.bruker?.id),
                lagretJournalpost.sedHendelseType,
                lagretJournalpost.sedHendelse,
                response,
                lagretJournalpost.journalpostRequest.journalfoerendeEnhet!!,
                lagretJournalpost.journalpostRequest.bruker?.id,
                lagretJournalpost.journalpostRequest.tema
            )
        }
    }

    /**
     * Forenklet TestRunner for testing av Journalføring med kun én person.
     *
     * @param fnr: Hovedperson/forsikrede sitt fnr.
     * @param saker: En liste med saker som skal returneres på søkeren sin aktørId. (Default = emptyList())
     * @param sakId: SakID tilknyttet bestemt sak som skal uthentes. (Default = [SAK_ID])
     * @param land: Landet person tilhører. (Default = "NOR")
     * @param assertBlock: En [Unit] for å kjøre assertions/validering på [OpprettJournalpostRequest]
     */
    protected fun testRunner(
        fnr: String?,
        saker: List<SakInformasjon> = emptyList(),
        sakId: String? = SAK_ID,
        land: String = "NOR",
        hendelseType: HendelseType = SENDT,
        bucType: BucType = P_BUC_01,
        sedType: SedType = SedType.P8000,
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = SED.generateSedToClass<P8000>(createSed(sedType = sedType, fnr = fnr, eessiSaknr = sakId))
        initCommonMocks(sed, bucType = bucType)

        every { personService.harAdressebeskyttelse(any()) } returns false

        if (fnr != null) {
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(
                fnr,
                "Fornavn",
                "Etternavn",
                land,
                aktorId = AKTOER_ID
            )
        }

        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit
        every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(sakId)
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { gcpStorageService.hentFraGjenny(any()) } returns null

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000, rinaDokumentId = "44cb68f89a2f4e748934fb4722721018")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val journalpostRequest = slot<OpprettJournalpostRequest>()

        if (hendelseType == SENDT)
            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        createMockedJournalPostWithOppgave(journalpostRequest, hendelse, hendelseType)

        assertBlock(journalpost.captured)

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        val gyldigFnr: Boolean = fnr != null && fnr.length == 11
        val antallKallTilPensjonSaklist = if (gyldigFnr) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

        clearAllMocks()
    }

    protected fun testRunnerPBuc06(
        fnr: String?,
        saker: List<SakInformasjon> = emptyList(),
        sakId: String? = SAK_ID,
        land: String = "NOR",
        hendelseType: HendelseType = SENDT,
        bucType: BucType = P_BUC_01,
        sedType: SedType = SedType.P8000,
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = SED.generateSedToClass<P8000>(createSed(sedType = sedType, fnr = fnr, eessiSaknr = sakId))
        initCommonMocks(sed, bucType = bucType)

        every { personService.harAdressebeskyttelse(any()) } returns false

        if (fnr != null) {
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(
                fnr,
                "Fornavn",
                "Etternavn",
                land,
                aktorId = AKTOER_ID
            )
        }

        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit
        every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(sakId)
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { gcpStorageService.hentFraGjenny(any()) } returns null


        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val journalpostRequest = slot<OpprettJournalpostRequest>()

        if (hendelseType == SENDT)
            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        createMockedJournalPostWithOppgave(journalpostRequest, hendelse, hendelseType)

        assertBlock(journalpost.captured)

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        fnr != null && fnr.length == 11

        clearAllMocks()
    }


    fun testRunnerVoksenMedSokPerson(
        fnrVoksen: String,
        benyttSokPerson: Boolean = true,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.UFOREP,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        block: (PBuc03IntegrationTest.TestResult) -> Unit
    ) {

        val mockBruker = createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)

        val fnrVoksensok = if (benyttSokPerson) null else fnrVoksen

        val sed = SED.generateSedToClass<P2200>(createSedPensjon(SedType.P2200, fnrVoksensok, eessiSaknr = sakId, krav = krav, pdlPerson = mockBruker, fdato = mockBruker.foedselsdato?.foedselsdato.toString()))
        initCommonMocks(sed, alleDocs, documentFiler)

        if (benyttSokPerson) {
            every { personService.sokPerson(any()) } returns setOf(
                IdentInformasjon(
                    fnrVoksen,
                    IdentGruppe.FOLKEREGISTERIDENT
                ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
            )
        }

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns mockBruker
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        sakId?.let {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(it)
        } ?: run {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()
        }
        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2200, P_BUC_03)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        }

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it)
        }
        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it)
        }
        block(PBuc03IntegrationTest.TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
        verify { personService.hentPerson(any()) }

        clearAllMocks()
    }


    fun getDokumentfilerUtenVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = javaClass.getResource("/pdf/pdfResponseUtenVedlegg.json")!!.readText()
        return mapJsonToAny(dokumentfilerJson)
    }

    fun initCommonMocks(sed: SED, alleDocs: List<ForenkletSED>, documentFiler: SedDokumentfiler, bucType: BucType = P_BUC_01, bucLand: String = "NO") {
        every { euxKlient.hentBuc(any()) } returns bucFrom(bucType, forenkletSed = alleDocs, bucLand)
        every { euxKlient.hentSedJson(any(), any()) } returns sed.toJson()
        every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns documentFiler
    }

    fun bucFrom(bucType: BucType, forenkletSed: List<ForenkletSED>, bucLand: String? = null): Buc {
        val part = if (bucLand != null) listOf(Participant(role = "CaseOwner", organisation = Organisation(name = bucLand, countryCode = bucLand))) else null
        return Buc(id = "2",  processDefinitionName = bucType.name, participants = part ,  documents = bucDocumentsFrom(forenkletSed))
    }
    fun bucDocumentsFrom(forenkletSed: List<ForenkletSED>): List<DocumentsItem> {
        return forenkletSed.map { forenklet -> DocumentsItem(id = forenklet.id, type = forenklet.type, status = forenklet.status?.name?.lowercase()) }
    }

    fun initCommonMocks(sed: SED, documents: List<ForenkletSED>? = null, bucType: BucType = P_BUC_01) {
        val docs = documents ?: mapJsonToAny(javaClass.getResource("/fagmodul/alldocumentsids.json")!!.readText())
        val dokumentVedleggJson = javaClass.getResource("/pdf/pdfResponseUtenVedlegg.json")!!.readText()
        val dokumentFiler = mapJsonToAny<SedDokumentfiler>(dokumentVedleggJson)
        initCommonMocks(sed, docs, dokumentFiler, bucType = bucType)
    }

    fun createBrukerWith(
        fnr: String?,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        land: String? = "NOR",
        geo: String = "1234",
        harAdressebeskyttelse: Boolean = false,
        aktorId: String? = null
    ): PdlPerson {

        val foedselsdato  = if(Fodselsnummer.fra(fnr)?.erNpid == true)
            Foedselsdato(foedselsdato = "1988-07-12", metadata = mockk(relaxed = true)).also { println("XXX" + it.toJsonSkipEmpty()) }
        else
            fnr?.let {
                Foedselsdato(foedselsdato = Fodselsnummer.fra(it)?.getBirthDate()?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), metadata = mockk(relaxed = true)).also { println("XXX" + it.toJsonSkipEmpty()) }
            }

        val utenlandskadresse = if (land == null || land == "NOR") null else UtenlandskAdresse(landkode = land)

        val identer = listOfNotNull(
            fnr?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.FOLKEREGISTERIDENT) },
            aktorId?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.AKTORID) }
        )

        val adressebeskyttelse = if (harAdressebeskyttelse) listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        else listOf(AdressebeskyttelseGradering.UGRADERT)

        val metadata = Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )

        return PdlPerson(
            identer = identer,
            navn = Navn(
                fornavn = fornavn, etternavn = etternavn, metadata = metadata
            ),
            adressebeskyttelse = adressebeskyttelse,
            bostedsadresse = Bostedsadresse(
                gyldigFraOgMed = LocalDateTime.now(),
                gyldigTilOgMed = LocalDateTime.now(),
                vegadresse = Vegadresse("Oppoverbakken", "66", null, "1920"),
                utenlandskAdresse = utenlandskadresse,
                metadata
            ),
            oppholdsadresse = null,
            statsborgerskap = emptyList(),
            foedselsdato = foedselsdato,
            foedested = Foedested(foedeland = "NOR", foedested = "OSLO", metadata = metadata),
            geografiskTilknytning = GeografiskTilknytning(GtType.KOMMUNE, geo),
            kjoenn = Kjoenn(KjoennType.KVINNE, metadata = metadata),
            doedsfall = null,
            forelderBarnRelasjon = emptyList(),
            sivilstand = emptyList(),
            utenlandskIdentifikasjonsnummer = emptyList()
        )
    }

    protected fun initJournalPostRequestSlot(ferdigstilt: Boolean = false): Pair<CapturingSlot<OpprettJournalpostRequest>, OpprettJournalPostResponse> {
        val request = slot<OpprettJournalpostRequest>()
        val journalpostResponse = OpprettJournalPostResponse("429434378", "M", null, ferdigstilt)

        every { journalpostKlient.opprettJournalpost(capture(request), any(), any()) } returns journalpostResponse

        return request to journalpostResponse
    }

    protected fun createAnnenPerson(
        fnr: String? = null,
        rolle: Rolle? = Rolle.ETTERLATTE,
        relasjon: RelasjonTilAvdod? = null,
        pdlPerson: PdlPerson? = null,
        fdato: String? ="1962-07-18"
    ): Person {
        if (fnr != null && fnr.isBlank()) {
            return Person(
                foedselsdato = fdato,
                rolle = rolle?.kode,
                relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) }
            )
        }
        val validFnr = Fodselsnummer.fra(fnr)

        return Person(
            validFnr?.let { listOf(PinItem(land = "NO", identifikator = it.value)) },
            foedselsdato = validFnr?.getBirthDateAsIso() ?: fdato,
            rolle = rolle?.kode,
            relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) },
            fornavn = "${pdlPerson?.navn?.fornavn}",
            etternavn = "${pdlPerson?.navn?.etternavn}"
        )
    }

    protected fun createSed(
        sedType: SedType,
        fnr: String? = null,
        annenPerson: Person? = null,
        eessiSaknr: String? = null,
        fdato: String? = "1988-07-12",
        pdlPerson: PdlPerson? = null
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val pdlForsikret = if (annenPerson == null) pdlPerson else null

        val forsikretBruker = Bruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = validFnr?.getBirthDateAsIso() ?: fdato,
                fornavn = "${pdlForsikret?.navn?.fornavn}",
                etternavn = "${pdlForsikret?.navn?.etternavn}"
            )
        )

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = forsikretBruker,
                annenperson = annenPerson?.let { Bruker(person = it) }
            )
        )
    }

    protected fun createSedPensjon(
        sedType: SedType,
        fnr: String?,
        eessiSaknr: String? = null,
        gjenlevendeFnr: String? = null,
        krav: KravType? = null,
        relasjon: RelasjonTilAvdod? = null,
        pdlPerson: PdlPerson? = null,
        sivilstand: SivilstandItem? = null,
        statsborgerskap: StatsborgerskapItem? = null,
        fdato: String? = null,
        fdatoAnnenPerson: String? = null
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val pdlPersonAnnen = if (relasjon != null) pdlPerson else null
        val pdlForsikret = if (relasjon == null) pdlPerson else null

        val foedselsdato  = if(Fodselsnummer.fra(fnr)?.erNpid == true){
            "1988-07-12"
        }
        else {
            fdato ?: validFnr?.let { Fodselsnummer.fra(fnr)?.getBirthDateAsIso() } ?: "1988-07-12"
        }

        val forsikretBruker = Bruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = foedselsdato,
                fornavn = pdlForsikret?.navn?.fornavn,
                etternavn = pdlForsikret?.navn?.etternavn,
                sivilstand = createSivilstand(sivilstand),
                statsborgerskap = createStatsborger(statsborgerskap)
            )
        )


        val annenPerson = Bruker(person = createAnnenPerson(gjenlevendeFnr, relasjon = relasjon, pdlPerson = pdlPersonAnnen, fdato = fdatoAnnenPerson))

        val pensjon = if (gjenlevendeFnr != null || pdlPersonAnnen != null) {
            if (sedType == SedType.P12000) {
                P12000Pensjon(listOf(Pensjoninfo(Betalingsdetaljer(pensjonstype = "02"))), gjenlevende = annenPerson)
            }
            Pensjon(gjenlevende = annenPerson)
        } else {
            null
        }

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = forsikretBruker,
                krav = Krav("2019-02-01", krav)
            ),
            pensjon = pensjon
        )
    }
    private fun createSivilstand(sivilstand: SivilstandItem?): List<SivilstandItem>? = if (sivilstand != null) listOf(sivilstand) else null

    private fun createStatsborger(statsborgerskap: StatsborgerskapItem?): List<StatsborgerskapItem>? = if (statsborgerskap != null) listOf(statsborgerskap) else null

    protected fun createHendelseJson(
        sedType: SedType,
        bucType: BucType = P_BUC_05,
        forsikretFnr: String? = null,
        rinaDokumentId: String? = "b12e06dda2c7474b9998c7139c841646",
    ): String {
        return """
            {
              "id": 1869,
              "sedId": "${sedType.name}_b12e06dda2c7474b9998c7139c841646_2",
              "sektorKode": "P",
              "bucType": "${bucType.name}",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "SE",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "$rinaDokumentId",
              "rinaDokumentVersjon": "2",
              "sedType": "${sedType.name}",
              "navBruker": ${forsikretFnr?.let { "\"$it\"" }}
            }
        """.trimIndent()
    }
}
