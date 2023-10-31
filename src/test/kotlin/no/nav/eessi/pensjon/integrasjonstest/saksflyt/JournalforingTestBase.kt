package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.automatisering.StatistikkPublisher
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.handler.BehandleHendelseModel
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.KravInitialiseringsService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.journalpost.*
import no.nav.eessi.pensjon.klienter.navansatt.NavansattKlient
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.listeners.SedSendtListener
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person as PdlPerson

internal open class JournalforingTestBase {

    companion object {
        const val SAK_ID = "12345"

        const val FNR_OVER_62 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN_UNDER_62 = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"
    }

    protected val euxKlient: EuxKlientLib = mockk()
    protected val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    protected val navansattKlient: NavansattKlient = mockk(relaxed = true)
    {
        every { navAnsattMedEnhetsInfo(any(), any()) } returns null
    }
    private val dokumentHelper = EuxService(euxKlient)
    private val fagmodulService = FagmodulService(fagmodulKlient)

    protected val norg2Service: Norg2Service = mockk(relaxed = true)
    protected val journalpostKlient: JournalpostKlient = mockk(relaxed = true, relaxUnitFun = true)

    private val journalpostService = JournalpostService(journalpostKlient)
    val oppgaveRoutingService: OppgaveRoutingService = OppgaveRoutingService(norg2Service)

    private val pdfService: PDFService = PDFService(dokumentHelper)

    protected val oppgaveHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    protected val kravInitHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val oppgaveHandler: OppgaveHandler = OppgaveHandler(oppgaveKafkaTemplate = oppgaveHandlerKafka)
    private val kravHandler = KravInitialiseringsHandler(kravInitHandlerKafka)
    private val kravService = KravInitialiseringsService(kravHandler)
    protected val automatiseringHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }
    private val statistikkPublisher = StatistikkPublisher(automatiseringHandlerKafka)
    private val journalforingService: JournalforingService = JournalforingService(
        journalpostService = journalpostService,
        oppgaveRoutingService = oppgaveRoutingService,
        pdfService = pdfService,
        oppgaveHandler = oppgaveHandler,
        kravInitialiseringsService = kravService,
        statistikkPublisher = statistikkPublisher,
    )

    protected val personService: PersonService = mockk(relaxed = true)
    private val personSok = PersonSok(personService)
    private val personidentifiseringService = PersonidentifiseringService(personSok, personService)


    protected val bestemSakKlient: BestemSakKlient = mockk(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)

    protected val mottattListener: SedMottattListener = SedMottattListener(
        journalforingService = journalforingService,
        personidentifiseringService = personidentifiseringService,
        dokumentHelper = dokumentHelper,
        fagmodulService = fagmodulService,
        bestemSakService = bestemSakService,
        profile = "test",
    )
    protected val sendtListener: SedSendtListener = SedSendtListener(
        journalforingService = journalforingService,
        personidentifiseringService = personidentifiseringService,
        dokumentHelper = dokumentHelper,
        bestemSakService = bestemSakService,
        fagmodulService = fagmodulService,
        profile = "test",
        navansattKlient = navansattKlient
    )


    @BeforeEach
    fun setup() {
        ReflectionTestUtils.setField(kravHandler, "kravTopic", "kravTopic")

        sendtListener.initMetrics()
        mottattListener.initMetrics()
        journalforingService.initMetrics()
        journalforingService.nameSpace = "test"
        pdfService.initMetrics()
        oppgaveHandler.initMetrics()
        kravHandler.initMetrics()
        bestemSakKlient.initMetrics()
        personSok.initMetrics()
        personService.initMetrics()
        dokumentHelper.initMetrics()
    }

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
     * @param diskresjonkode: Diskresjonskoden knyttet til annen person. (Default = null)
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

        every { personService.harAdressebeskyttelse(any(), any()) } returns harAdressebeskyttelse
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null


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

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        if (hendelseType == SENDT)
            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        // forvent tema == PEN og enhet 2103
        val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        assertEquals(hendelseType, oppgaveMelding.hendelseType)

        val request = journalpost.captured

        assertBlock(request)

        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }

        if (hendelseType == SENDT) {
            assertEquals(JournalpostType.UTGAAENDE, request.journalpostType)

            val antallPersoner = listOfNotNull(fnr, fnrAnnenPerson).size
            val antallKallTilPensjonSaklist = if (antallPersoner > 0 && sakId != null) 1 else 0
            verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }
        } else {
            assertEquals(JournalpostType.INNGAAENDE, request.journalpostType)

            verify(exactly = 0) { fagmodulKlient.hentPensjonSaklist(any()) }
        }

        clearAllMocks()
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
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = SED.generateSedToClass<P8000>(createSed(sedType = SedType.P8000, fnr = fnr, eessiSaknr = sakId))
        initCommonMocks(sed, bucType = bucType)

        every { personService.harAdressebeskyttelse(any(), any()) } returns false

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

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        if (hendelseType == SENDT)
            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        assertBlock(journalpost.captured)

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        val gyldigFnr: Boolean = fnr != null && fnr.length == 11
        val antallKallTilPensjonSaklist = if (gyldigFnr && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

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

        val sed = SED.generateSedToClass<P2200>(createSedPensjon(SedType.P2200, fnrVoksensok, eessiSaknr = sakId, krav = krav, pdlPerson = mockBruker, fdato = mockBruker.foedsel?.foedselsdato.toString()))
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

        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2200, P_BUC_03)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> Assertions.fail()
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
        val dokumentfilerJson = getResource("/pdf/pdfResponseUtenVedlegg.json")
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
        val docs = documents ?: mapJsonToAny(getResource("/fagmodul/alldocumentsids.json"))
        val dokumentVedleggJson = getResource("/pdf/pdfResponseUtenVedlegg.json")
        val dokumentFiler = mapJsonToAny<SedDokumentfiler>(dokumentVedleggJson)
        initCommonMocks(sed, docs, dokumentFiler, bucType = bucType)
    }

    private fun getResource(resourcePath: String): String = javaClass.getResource(resourcePath).readText()

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
            LocalDate.of(1988,7,12)
        else
            fnr?.let { Fodselsnummer.fra(it)?.getBirthDate() }

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
            foedsel = Foedsel(foedselsdato, "NOR", "OSLO", metadata = metadata),
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
        pdlPerson: no.nav.eessi.pensjon.personoppslag.pdl.model.Person? = null,
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
            Pensjon(gjenlevende = annenPerson)
        }  else {
            null
        }

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = forsikretBruker,
                krav = Krav("2019-02-01", krav?.verdi)
            ),
            pensjon = pensjon
        )
    }
    private fun createSivilstand(sivilstand: SivilstandItem?): List<SivilstandItem>? = if (sivilstand != null) listOf(sivilstand) else null

    private fun createStatsborger(statsborgerskap: StatsborgerskapItem?): List<StatsborgerskapItem>? = if (statsborgerskap != null) listOf(statsborgerskap) else null

    protected fun createHendelseJson(
        sedType: SedType,
        bucType: BucType = P_BUC_05,
        forsikretFnr: String? = null
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
              "rinaDokumentId": "b12e06dda2c7474b9998c7139c841646",
              "rinaDokumentVersjon": "2",
              "sedType": "${sedType.name}",
              "navBruker": ${forsikretFnr?.let { "\"$it\"" }}
            }
        """.trimIndent()
    }

    fun hentEsssisaknr(bestemSak: BestemSakResponse?): String? = if (bestemSak?.sakInformasjonListe?.size == 1) {
            bestemSak.sakInformasjonListe.first().sakId
        } else {
            null
        }

}
