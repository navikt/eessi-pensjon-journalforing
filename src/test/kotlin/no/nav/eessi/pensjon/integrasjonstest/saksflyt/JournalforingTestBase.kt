package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.sed.DocStatus
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.EessisakItem
import no.nav.eessi.pensjon.models.sed.Krav
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.Nav
import no.nav.eessi.pensjon.models.sed.Pensjon
import no.nav.eessi.pensjon.models.sed.Person
import no.nav.eessi.pensjon.models.sed.PinItem
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.models.sed.Rolle
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.Norg2Klient
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSøk
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bostedsadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Gateadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Land
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import no.nav.eessi.pensjon.models.sed.Bruker as SedBruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent as PersonV3NorskIdent

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

    protected val euxKlient: EuxKlient = mockk()
    private val norg2Klient: Norg2Klient = mockk(relaxed = true)

    private val journalpostKlient: JournalpostKlient = mockk(relaxed = true, relaxUnitFun = true)

    private val journalpostService = JournalpostService(journalpostKlient)
    private val oppgaveRoutingService: OppgaveRoutingService = OppgaveRoutingService(norg2Klient)
    private val pdfService: PDFService = PDFService()

    protected val oppgaveHandlerKafka: KafkaTemplate<String, String> = mockk(relaxed = true) {
        every { sendDefault(any(), any()).get() } returns mockk()
    }

    private val oppgaveHandler: OppgaveHandler = OppgaveHandler(kafkaTemplate = oppgaveHandlerKafka)
    private val journalforingService: JournalforingService = JournalforingService(
            euxKlient = euxKlient,
            journalpostService = journalpostService,
            oppgaveRoutingService = oppgaveRoutingService,
            pdfService = pdfService,
            oppgaveHandler = oppgaveHandler
    )

    protected val aktoerregisterService: AktoerregisterService = mockk(relaxed = true)
    protected val personV3Service: PersonV3Service = mockk()
    private val diskresjonService: DiskresjonkodeHelper = spyk(DiskresjonkodeHelper(personV3Service, SedFnrSøk()))

    private val personidentifiseringService = PersonidentifiseringService(
            aktoerregisterService, personV3Service, diskresjonService, FnrHelper()
    )

    protected val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    private val sedDokumentHelper = SedDokumentHelper(fagmodulKlient, euxKlient)
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

        listener.initMetrics()
        journalforingService.initMetrics()
        pdfService.initMetrics()
        oppgaveHandler.initMetrics()
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
            diskresjonkode: Diskresjonskode? = null,
            land: String = "NOR",
            rolle: Rolle?,
            hendelseType: HendelseType = HendelseType.SENDT,
            assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        initSed(createSed(SedType.P8000, fnr, createAnnenPerson(fnr = fnrAnnenPerson, rolle = rolle), sakId))
        initDokumenter(getMockDocuments())

        if (fnr != null)
            initMockPerson(fnr, aktoerId = AKTOER_ID, land = land)

        if (fnrAnnenPerson != null)
            initMockPerson(fnrAnnenPerson, aktoerId = AKTOER_ID_2, land = land, diskresjonskode = diskresjonkode)

        // returnere saker på gjenlevende/etterlatte
        if (rolle == Rolle.ETTERLATTE) initSaker(AKTOER_ID_2, saker)
        else initSaker(AKTOER_ID, saker)

        consumeAndAssert(hendelseType, SedType.P8000, bucType = BucType.P_BUC_05) {
            assertBlock(it.request)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

//        val antallPersoner = listOfNotNull(fnr, fnrAnnenPerson).size
//        val antallKallIdent = if (antallPersoner == 0) 0
//        else antallPersoner - (1.takeIf { rolle == "01" } ?: 0)
//        verify(exactly = antallKallIdent) { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }


//        val antallKallHentPerson = (antallPersoner + (1.takeIf { antallPersoner > 0 && rolle != null } ?: 0)) * 2
//        verify(exactly = antallKallHentPerson) { personV3Service.hentPerson(any()) }

        /*if (hendelseType == HendelseType.SENDT) {
            assertEquals(JournalpostType.UTGAAENDE, request.journalpostType)

            val antallKallTilPensjonSaklist = if (antallPersoner > 0 && sakId != null) 1 else 0
            verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }
        } else {
            assertEquals(JournalpostType.INNGAAENDE, request.journalpostType)

            verify(exactly = 0) { fagmodulKlient.hentPensjonSaklist(any()) }
        }*/

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
        initSed(createSed(SedType.P8000, fnr, eessiSaknr = sakId))
        initDokumenter(getMockDocuments())

        if (fnr != null) initMockPerson(fnr, aktoerId = AKTOER_ID, land = land)

        initSaker(AKTOER_ID, saker)

//        val (journalpost, _) = initJournalPostRequestSlot(true)
//
//        val hendelse = createHendelseJson(SedType.P8000)
//
//        val meldingSlot = slot<String>()
//        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
//
//        if (hendelseType == HendelseType.SENDT)
//            listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
//        else
//            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
//
//        assertBlock(journalpost.captured)
//
        consumeAndAssert(hendelseType, SedType.P8000, BucType.P_BUC_05) {
            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
            verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }
        }

        val gyldigFnr: Boolean = fnr != null && fnr.length == 11
        val antallKallTilPensjonSaklist = if (gyldigFnr && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

        val antallKallAktoerreg = if (gyldigFnr) 1 else 0
        verify(exactly = antallKallAktoerreg) { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }

        clearAllMocks()
    }

    private fun createBrukerWith(fnr: String?, fornavn: String = "Fornavn", etternavn: String = "Etternavn", land: String? = "NOR", geo: String = "1234", diskresjonskode: String? = null): Bruker {
        return Bruker()
                .withPersonnavn(Personnavn().withSammensattNavn("$fornavn $etternavn"))
                .withGeografiskTilknytning(Land().withGeografiskTilknytning(geo) as GeografiskTilknytning)
                .withAktoer(PersonIdent().withIdent(PersonV3NorskIdent().withIdent(fnr)))
                .withBostedsadresse(Bostedsadresse().withStrukturertAdresse(Gateadresse().withLandkode(Landkoder().withValue(land))))
                .withDiskresjonskode(Diskresjonskoder().withValue(diskresjonskode))
    }

    protected fun createAnnenPerson(fnr: String? = null,
                                    rolle: Rolle? = Rolle.ETTERLATTE,
                                    relasjon: String? = null): Person {
        if (fnr != null && fnr.isBlank()) {
            return Person(
                    foedselsdato = "1962-07-18",
                    rolle = rolle,
                    relasjontilavdod = relasjon?.let { RelasjonTilAvdod(it) }
            )
        }
        val validFnr = Fodselsnummer.fra(fnr)

        return Person(
                validFnr?.let { listOf(PinItem(land = "NO", identifikator = it.value)) },
                foedselsdato = validFnr?.getBirthDateAsIso() ?: "1962-07-18",
                rolle = rolle,
                relasjontilavdod = relasjon?.let { RelasjonTilAvdod(it) }
        )
    }

    protected fun createSed(sedType: SedType,
                            fnr: String? = null,
                            annenPerson: Person? = null,
                            eessiSaknr: String? = null,
                            fdato: String? = "1988-07-12"): SED {
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

    protected fun createSedPensjon(sedType: SedType,
                                   fnr: String?,
                                   eessiSaknr: String? = null,
                                   gjenlevendeFnr: String? = null,
                                   krav: KravType? = null,
                                   relasjon: String? = null): SED {
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

    private fun createHendelseJson(sedType: SedType, bucType: BucType = BucType.P_BUC_05, forsikretFnr: String? = null): String {
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

    protected fun getMockDocuments(): List<Document> {
        return listOf(
                Document("44cb68f89a2f4e748934fb4722721018", SedType.P2000, DocStatus.SENT),
                Document("3009f65dd2ac4948944c6b7cfa4f179d", SedType.H121, null),
                Document("9498fc46933548518712e4a1d5133113", SedType.H070, null)
        )
    }


    /*
    * TODO: Finish new variant of simplification
    */

    protected fun consumeAndAssert(hendelseType: HendelseType,
                                   sedType: SedType,
                                   bucType: BucType,
                                   hendelseFnr: String? = null,
                                   ferdigstilt: Boolean = false,
                                   assertBlock: (TestScope) -> Unit
    ) {
        // SLOTS
        val meldingSlot = slot<String>()
        val request = slot<OpprettJournalpostRequest>()

        // RESPONSE
        val journalpostResponse = OpprettJournalPostResponse("429434378", "M", null, ferdigstilt)

        // MISC MOCK RETURNS
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { journalpostKlient.opprettJournalpost(capture(request), any()) } returns journalpostResponse

        // SED HENDELSE
        val hendelseJson = createHendelseJson(sedType, bucType, forsikretFnr = hendelseFnr)
        if (hendelseType == HendelseType.SENDT) {
            listener.consumeSedSendt(hendelseJson, mockk(relaxed = true), mockk(relaxed = true))
        } else {
            listener.consumeSedMottatt(hendelseJson, mockk(relaxed = true), mockk(relaxed = true))
        }

        // TODO: Verify
        val oppgaveMelding = if (ferdigstilt) {
//            verify(exactly = 1) { journalpostService.oppdaterDistribusjonsinfo(any()) }
            null
        } else {
//            verify(exactly = 0) { journalpostService.oppdaterDistribusjonsinfo(any()) }
            mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        }

        // Lagre Request, Response, og OppgaveMelding i et objekt for validering
        assertBlock(TestScope(request.captured, journalpostResponse, oppgaveMelding))
    }

    protected fun initSaker(aktoerId: String, saker: List<SakInformasjon>) {
        every { fagmodulKlient.hentPensjonSaklist(aktoerId) } returns saker
    }

    protected fun initSaker(aktoerId: String, vararg sak: SakInformasjon) {
        every { fagmodulKlient.hentPensjonSaklist(aktoerId) } returns sak.asList()
    }

    protected fun initBestemSak(vararg sak: SakInformasjon) {
        every { bestemSakKlient.kallBestemSak(any()) } returns BestemSakResponse(null, sak.asList())
    }

    protected fun initMockPerson(fnr: String,
                                 aktoerId: String?,
                                 land: String = "NOR",
                                 diskresjonskode: Diskresjonskode? = null) {

        every { personV3Service.hentPerson(fnr) } returns aktoerId?.run { createBrukerWith(fnr, "Fornavn", "Etternavnsen", land) }
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns aktoerId?.let { AktoerId(it) }
        every { diskresjonService.hentDiskresjonskode(any()) } returns diskresjonskode
    }

    protected fun initSed(sed: SED, vararg andreSeds: SED) {
        every { euxKlient.hentSed(any(), any()) }
                .returns(sed)
                .run { andreSeds.forEach { andThen(it) } }
    }

    protected fun initDokumenter(vararg dokument: Document) {
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns dokument.toList()
    }

    protected fun initDokumenter(dokumenter: List<Document>) {
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns dokumenter
    }

    protected fun getResource(resourcePath: String): String =
            javaClass.getResource(resourcePath).readText()

    protected inner class TestScope(
            val request: OpprettJournalpostRequest,
            val response: OpprettJournalPostResponse, // TODO: remove?
            val melding: OppgaveMelding?
    )
}
