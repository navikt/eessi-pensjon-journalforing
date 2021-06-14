package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.handler.BehandleHendelseModel
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.*
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person as PdlPerson

internal open class JournalforingTestBase {

    companion object {
        const val SAK_ID = "12345"

        const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"
    }

    protected val euxService: EuxService = mockk()
    protected val norg2Service: Norg2Service = mockk(relaxed = true)

    protected val journalpostKlient: JournalpostKlient = mockk(relaxed = true, relaxUnitFun = true)

    private val journalpostService = JournalpostService(journalpostKlient)
    val oppgaveRoutingService: OppgaveRoutingService = OppgaveRoutingService(norg2Service)

    private val pdfService: PDFService = PDFService(euxService)

    protected val oppgaveHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    protected val kravInitHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val oppgaveHandler: OppgaveHandler = OppgaveHandler(kafkaTemplate = oppgaveHandlerKafka)
    private val kravHandler = KravInitialiseringsHandler(kravInitHandlerKafka)
    private val journalforingService: JournalforingService = JournalforingService(
        journalpostService = journalpostService,
        oppgaveRoutingService = oppgaveRoutingService,
        pdfService = pdfService,
        oppgaveHandler = oppgaveHandler,
        kravInitialiseringsHandler = kravHandler
    )

    protected val personService: PersonService = mockk(relaxed = true)

    protected val personidentifiseringService = PersonidentifiseringService(personService, FnrHelper())


    protected val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    private val sedDokumentHelper = SedDokumentHelper(fagmodulKlient, euxService)
    protected val bestemSakKlient: BestemSakKlient = mockk(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)


    protected val listener: SedListener = SedListener(
        journalforingService = journalforingService,
        personidentifiseringService = personidentifiseringService,
        sedDokumentHelper = sedDokumentHelper,
        bestemSakService = bestemSakService,
        profile = "test"
    )

    @BeforeEach
    fun setup() {
        ReflectionTestUtils.setField(journalpostService, "navOrgnummer", "999999999")
        ReflectionTestUtils.setField(oppgaveHandler, "oppgaveTopic", "oppgaveTopic")
        ReflectionTestUtils.setField(kravHandler, "kravTopic", "kravTopic")

        listener.initMetrics()
        journalforingService.initMetrics()
        journalforingService.nameSpace = "test"
        pdfService.initMetrics()
        oppgaveHandler.initMetrics()
        kravHandler.initMetrics()
        bestemSakKlient.initMetrics()
        personidentifiseringService.nameSpace = "test"
        personidentifiseringService.initMetrics()

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
        hendelseType: HendelseType = HendelseType.SENDT,
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sedjson = createSed(SedType.P8000, fnr, createAnnenPerson(fnr = fnrAnnenPerson, rolle = rolle), sakId).toJson()
        val sed = hentSed(sedjson)

        initCommonMocks(sed)

        every { euxService.hentBuc (any()) } returns mockk(relaxed = true)

        every { personService.harAdressebeskyttelse(any(), any()) } returns harAdressebeskyttelse

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

        if (hendelseType == HendelseType.SENDT)
            listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        // forvent tema == PEN og enhet 2103
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        assertEquals(hendelseType, oppgaveMelding.hendelseType)

        val request = journalpost.captured

        assertBlock(request)

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        verify(exactly = 1) { euxService.hentSed(any(), any()) }

        if (hendelseType == HendelseType.SENDT) {
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


    fun hentSed(json: String): SED {
        val sed = json.let { mapJsonToAny(it, typeRefs<SED>()) }
        return when(sed!!.type) {
            SedType.P2200 -> mapJsonToAny(json, typeRefs<P2200>())
            SedType.P4000 -> mapJsonToAny(json, typeRefs<P4000>())
            SedType.P5000 -> mapJsonToAny(json, typeRefs<P5000>())
            SedType.P6000 -> mapJsonToAny(json, typeRefs<P6000>())
            SedType.P7000 -> mapJsonToAny(json, typeRefs<P7000>())
            SedType.P8000 -> mapJsonToAny(json, typeRefs<P8000>())
            SedType.P10000 -> mapJsonToAny(json, typeRefs<P10000>())
            SedType.P15000 -> mapJsonToAny(json, typeRefs<P15000>())
            SedType.X005 -> mapJsonToAny(json, typeRefs<X005>())
            SedType.R005 -> mapJsonToAny(json, typeRefs<R005>())
            else -> sed
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
        hendelseType: HendelseType = HendelseType.SENDT,
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sedjson = createSed(SedType.P8000, fnr, eessiSaknr = sakId).toJson()
        val sed = hentSed(sedjson)
        initCommonMocks(sed)

        every { euxService.hentBuc (any()) } returns mockk(relaxed = true)
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
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        if (hendelseType == HendelseType.SENDT)
            listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        assertBlock(journalpost.captured)

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        verify(exactly = 1) { euxService.hentSed(any(), any()) }
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
        krav: KravType = KravType.UFORE,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        block: (PBuc03IntegrationTest.TestResult) -> Unit
    ) {

        val mockBruker = createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)

        val fnrVoksensok = if (benyttSokPerson) null else fnrVoksen
        val sedjson = createSedPensjon(SedType.P2200, fnrVoksensok, eessiSaknr = sakId, krav = krav, pdlPerson = mockBruker).toJson()
        val sed = hentSed(sedjson)

        initCommonMocks(sed, alleDocs, documentFiler)

        if (benyttSokPerson) {
            every { personService.sokPerson(any()) } returns setOf(
                IdentInformasjon(
                    fnrVoksen,
                    IdentGruppe.FOLKEREGISTERIDENT
                ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
            )
        }

        every { euxService.hentBuc (any()) } returns mockk(relaxed = true)
        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns mockBruker
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2200, BucType.P_BUC_03)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()

        when (hendelseType) {
            HendelseType.SENDT -> listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            HendelseType.MOTTATT -> listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> Assertions.fail()
        }

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it, typeRefs<BehandleHendelseModel>())
        }
        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it, typeRefs<OppgaveMelding>())
        }
        block(PBuc03IntegrationTest.TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxService.hentSed(any(), any()) }

        clearAllMocks()
    }


    fun getDokumentfilerUtenVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = getResource("/pdf/pdfResponseUtenVedlegg.json")
        return mapJsonToAny(dokumentfilerJson, typeRefs())
    }

    fun initCommonMocks(sed: SED, alleDocs: List<ForenkletSED>, documentFiler: SedDokumentfiler) {
        every { euxService.hentBucDokumenter(any()) } returns alleDocs
        every { euxService.hentSed(any(), any()) } returns sed
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns documentFiler
    }

    fun initCommonMocks(sed: SED, documents: List<ForenkletSED>? = null) {
        val docs = documents ?: mapJsonToAny(getResource("/fagmodul/alldocumentsids.json"), typeRefs<List<ForenkletSED>>())

        val dokumentVedleggJson = getResource("/pdf/pdfResponseUtenVedlegg.json")
        val dokumentFiler = mapJsonToAny(dokumentVedleggJson, typeRefs<SedDokumentfiler>())
        initCommonMocks(sed, docs, dokumentFiler)
    }

    private fun getResource(resourcePath: String): String = javaClass.getResource(resourcePath).readText()

    protected fun createBrukerWith(
        fnr: String?,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        land: String? = "NOR",
        geo: String = "1234",
        harAdressebeskyttelse: Boolean = false,
        aktorId: String? = null
    ): PdlPerson {

        val foedselsdato = fnr?.let { Fodselsnummer.fra(it)?.getBirthDate() }
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
            sivilstand = emptyList()
        )
    }

    protected fun initJournalPostRequestSlot(ferdigstilt: Boolean = false): Pair<CapturingSlot<OpprettJournalpostRequest>, OpprettJournalPostResponse> {
        val request = slot<OpprettJournalpostRequest>()
        val journalpostResponse = OpprettJournalPostResponse("429434378", "M", null, ferdigstilt)

        every { journalpostKlient.opprettJournalpost(capture(request), any()) } returns journalpostResponse

        return request to journalpostResponse
    }

    protected fun createAnnenPerson(
        fnr: String? = null,
        rolle: Rolle? = Rolle.ETTERLATTE,
        relasjon: RelasjonTilAvdod? = null,
        pdlPerson: PdlPerson? = null
    ): Person {
        if (fnr != null && fnr.isBlank()) {
            return Person(
                foedselsdato = "1962-07-18",
                rolle = rolle?.kode,
                relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) }
            )
        }
        val validFnr = Fodselsnummer.fra(fnr)

        return Person(
            validFnr?.let { listOf(PinItem(land = "NO", identifikator = it.value)) },
            foedselsdato = validFnr?.getBirthDateAsIso() ?: "1962-07-18",
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
        pdlPerson: PdlPerson? = null
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val pdlPersonAnnen = if (relasjon != null) pdlPerson else null
        val pdlForsikret = if (relasjon == null) pdlPerson else null

        val forsikretBruker = Bruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = validFnr?.getBirthDateAsIso() ?: "1988-07-12",
                fornavn = "${pdlForsikret?.navn?.fornavn}",
                etternavn = "${pdlForsikret?.navn?.etternavn}"
            )
        )

        val annenPerson = Bruker(person = createAnnenPerson(gjenlevendeFnr, relasjon = relasjon, pdlPerson = pdlPersonAnnen))

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = forsikretBruker,
                krav = Krav("2019-02-01", krav?.kode)
            ),
            pensjon = Pensjon(gjenlevende = annenPerson)
        )
    }

    protected fun createHendelseJson(
        sedType: SedType,
        bucType: BucType = BucType.P_BUC_05,
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
              "avsenderLand": "NO",
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

}
