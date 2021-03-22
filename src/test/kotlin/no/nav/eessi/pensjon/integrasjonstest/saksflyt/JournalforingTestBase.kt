package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import com.fasterxml.jackson.core.type.TypeReference
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.sed.EessisakItem
import no.nav.eessi.pensjon.models.sed.Krav
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.Nav
import no.nav.eessi.pensjon.models.sed.Pensjon
import no.nav.eessi.pensjon.models.sed.Person
import no.nav.eessi.pensjon.models.sed.PinItem
import no.nav.eessi.pensjon.models.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.models.sed.Rolle
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedsel
import no.nav.eessi.pensjon.personoppslag.pdl.model.GeografiskTilknytning
import no.nav.eessi.pensjon.personoppslag.pdl.model.GtType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import no.nav.eessi.pensjon.models.sed.Bruker as SedBruker
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
    private val oppgaveRoutingService: OppgaveRoutingService = OppgaveRoutingService(norg2Service)
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

    private val personidentifiseringService = PersonidentifiseringService(personService, FnrHelper())

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
        val sed = createSed(SedType.P8000, fnr, createAnnenPerson(fnr = fnrAnnenPerson, rolle = rolle), sakId)
        initCommonMocks(sed)

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
        verify(exactly = 1) { euxService.hentSed(any(), any(), any<TypeReference<SED>>()) }

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
        val sed = createSed(SedType.P8000, fnr, eessiSaknr = sakId)
        initCommonMocks(sed)

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
        verify(exactly = 1) { euxService.hentSed(any(), any(), any<TypeReference<SED>>()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        val gyldigFnr: Boolean = fnr != null && fnr.length == 11
        val antallKallTilPensjonSaklist = if (gyldigFnr && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

        clearAllMocks()
    }

    private fun initCommonMocks(sed: SED, documents: List<ForenkletSED>? = null) {
        val docs = if (documents == null || documents.isNullOrEmpty())
            mapJsonToAny(getResource("/fagmodul/alldocumentsids.json"), typeRefs<List<ForenkletSED>>())
        else documents

        every { euxService.hentBucDokumenter(any()) } returns docs
        every { euxService.hentSed(any(), any(), any<TypeReference<SED>>()) } returns sed

        val dokumentVedleggJson = getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns mapJsonToAny(dokumentVedleggJson, typeRefs())
    }

    private fun getResource(resourcePath: String): String =
        javaClass.getResource(resourcePath).readText()

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
            familierelasjoner = emptyList(),
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
        relasjon: RelasjonTilAvdod? = null
    ): Person {
        if (fnr != null && fnr.isBlank()) {
            return Person(
                foedselsdato = "1962-07-18",
                rolle = rolle,
                relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it) }
            )
        }
        val validFnr = Fodselsnummer.fra(fnr)

        return Person(
            validFnr?.let { listOf(PinItem(land = "NO", identifikator = it.value)) },
            foedselsdato = validFnr?.getBirthDateAsIso() ?: "1962-07-18",
            rolle = rolle,
            relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it) }
        )
    }

    protected fun createSed(
        sedType: SedType,
        fnr: String? = null,
        annenPerson: Person? = null,
        eessiSaknr: String? = null,
        fdato: String? = "1988-07-12"
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val forsikretBruker = SedBruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = validFnr?.getBirthDateAsIso() ?: fdato
            )
        )

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = listOf(forsikretBruker),
                annenperson = annenPerson?.let { SedBruker(person = it) }
            )
        )
    }

    protected fun createSedPensjon(
        sedType: SedType,
        fnr: String?,
        eessiSaknr: String? = null,
        gjenlevendeFnr: String? = null,
        krav: KravType? = null,
        relasjon: RelasjonTilAvdod? = null
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val forsikretBruker = SedBruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = validFnr?.getBirthDateAsIso() ?: "1988-07-12"
            )
        )

        val annenPerson = SedBruker(person = createAnnenPerson(gjenlevendeFnr, relasjon = relasjon))

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = listOf(forsikretBruker),
                krav = Krav("2019-02-01", krav)
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
