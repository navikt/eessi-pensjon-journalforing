package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.CapturingSlot
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
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.listeners.GyldigeFunksjonerToggleNonProd
import no.nav.eessi.pensjon.listeners.GyldigeHendelser
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.Norg2Klient
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.NavFodselsnummer
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSøk
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.service.buc.BucService
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bostedsadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Gateadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoenn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoennstyper
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Land
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Statsborgerskap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils

internal open class JournalforingTestBase {

    companion object {
        const val SAK_ID = "12345"

        const val FNR_OVER_60 = "01115043352"
        const val FNR_VOKSEN = "01119043352"
        const val FNR_VOKSEN_2 = "01118543352"
        const val FNR_BARN = "01110854352"

        private const val AKTOER_ID = "0123456789000"
        private const val AKTOER_ID_2 = "0009876543210"
    }

    protected val euxKlient: EuxKlient = mockk()
    private val norg2Klient: Norg2Klient = mockk(relaxed = true)

    protected val journalpostKlient: JournalpostKlient = mockk(relaxed = true, relaxUnitFun = true)

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
    protected val personV3Service: PersonV3Service = mockk(relaxed = true)
    protected val diskresjonService: DiskresjonkodeHelper = spyk(DiskresjonkodeHelper(personV3Service, SedFnrSøk()))

    private val personidentifiseringService = PersonidentifiseringService(
            aktoerregisterService, personV3Service, diskresjonService, FnrHelper(), FdatoHelper()
    )

    protected val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    private val sedDokumentHelper = SedDokumentHelper(fagmodulKlient, euxKlient)
    private val bestemSakKlient: BestemSakKlient = mockk(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)
    private val gyldigeFunksjoner = GyldigeFunksjonerToggleNonProd()
    private val bucService: BucService = mockk(relaxed = true)

    protected val listener: SedListener = SedListener(
            journalforingService = journalforingService,
            personidentifiseringService = personidentifiseringService,
            sedDokumentHelper = sedDokumentHelper,
            gyldigeHendelser = GyldigeHendelser(),
            bestemSakService = bestemSakService,
            gyldigeFunksjoner = gyldigeFunksjoner,
            profile = "test",
            bucService = bucService
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
            rolle: String?,
            hendelseType: HendelseType = HendelseType.SENDT,
            assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedJson(SedType.P8000, fnr, createAnnenPersonJson(fnr = fnrAnnenPerson, rolle = rolle), sakId)
        initCommonMocks(sed)

        if (fnr != null) {
            every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Mamma forsørger", "Etternavn", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent(fnr)) } returns AktoerId(AKTOER_ID)
        }

        if (fnrAnnenPerson != null) {
            every { personV3Service.hentPerson(fnrAnnenPerson) } returns createBrukerWith(fnrAnnenPerson, "Barn", "Diskret", land, "1213", diskresjonkode?.name)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent(fnrAnnenPerson)) } returns AktoerId(AKTOER_ID_2)
        }

        if (rolle == "01")
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
        if (hendelseType == HendelseType.SENDT)
            assertEquals(JournalpostType.UTGAAENDE, request.journalpostType)
        else
            assertEquals(JournalpostType.INNGAAENDE, request.journalpostType)

        assertBlock(request)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

        val antallPersoner = listOfNotNull(fnr, fnrAnnenPerson).size
        verify(exactly = antallPersoner) { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent>()) }

        val antallKallHentPerson = (antallPersoner + (1.takeIf { antallPersoner > 0 && rolle != null } ?: 0)) * 2
        verify(exactly = antallKallHentPerson) { personV3Service.hentPerson(any()) }

        val antallKallTilPensjonSaklist = if (antallPersoner > 0 && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

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
        val sed = createSedJson(SedType.P8000, fnr, null, sakId)
        initCommonMocks(sed)

        if (fnr != null) {
            every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Fornavn", "Etternavn", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent(fnr)) } returns AktoerId(AKTOER_ID)
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

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        val gyldigFnr: Boolean = sakId != null && fnr != null && fnr.length == 11
        val antallKallTilPensjonSaklist = if (gyldigFnr && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

        clearAllMocks()
    }

    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()

    protected fun createBrukerWith(fnr: String?, fornavn: String = "Fornavn", etternavn: String = "Etternavn", land: String? = "NOR", geo: String = "1234", diskresjonskode: String? = null): Bruker {
        return Bruker()
                .withPersonnavn(
                        Personnavn()
                                .withEtternavn(etternavn)
                                .withFornavn(fornavn)
                                .withSammensattNavn("$fornavn $etternavn")
                )
                .withGeografiskTilknytning(Land().withGeografiskTilknytning(geo) as GeografiskTilknytning)
                .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
                .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(fnr)))
                .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue(land)))
                .withBostedsadresse(Bostedsadresse().withStrukturertAdresse(Gateadresse().withLandkode(Landkoder().withValue(land))))
                .withDiskresjonskode(Diskresjonskoder().withValue(diskresjonskode))
    }

    private fun journalpostResponse(ferdigstilt: Boolean = false): OpprettJournalPostResponse {
        return OpprettJournalPostResponse(
                "429434378",
                "M",
                null,
                ferdigstilt
        )
    }

    protected fun initJournalPostRequestSlot(ferdigstilt: Boolean = false): Pair<CapturingSlot<OpprettJournalpostRequest>, OpprettJournalPostResponse> {
        val request = slot<OpprettJournalpostRequest>()
        val journalpostResponse = journalpostResponse(ferdigstilt)

        every { journalpostKlient.opprettJournalpost(capture(request), any()) } returns journalpostResponse

        return request to journalpostResponse
    }

    protected fun createAnnenPersonJson(fnr: String? = null, rolle: String? = "01"): String {
        val fdato = fnr?.let { NavFodselsnummer(it).getBirthDateAsISO() } ?: "1962-07-18"

        return """
            {
                "person": {
                      ${if (rolle != null) "\"rolle\" : \"$rolle\"," else ""}
                    "fornavn": "Annen",
                    "etternavn": "Person",
                    "kjoenn": "U",
                    "foedselsdato": "$fdato"
                    ${if (fnr != null) createPinJson(fnr) else ""}
                }
            }
        """.trimIndent()
    }

    private fun createPinJson(fnr: String?): String {
        return """
             ,"pin": [
                      {
                        "land": "NO",
                        "identifikator": "$fnr"
                      }
                    ]
        """.trimIndent()
    }

    private fun createEESSIsakJson(saknr: String?): String {
        return """
            "eessisak": [
              {
                "saksnummer": "$saknr",
                "land": "NO"
              }
            ],            
        """.trimIndent()
    }

    protected fun createSedJson(sedType: SedType, fnr: String? = null, annenPerson: String? = null, eessiSaknr: String? = null, fdato: String? = "1988-07-12"): String {

        return """
            {
              "nav": {
                ${if (eessiSaknr != null) createEESSIsakJson(eessiSaknr) else ""}
                "bruker": {
                  "adresse": {
                    "gate": "Oppoverbakken 66",
                    "land": "NO",
                    "by": "SØRUMSAND"
                  },
                  "person": {
                    "kjoenn": "M",
                    "etternavn": "Død",
                    "fornavn": "Avdød",
                    "foedselsdato": "$fdato"
                    ${if (fnr != null) createPinJson(fnr) else ""}
                  }
                },
                "annenperson": $annenPerson
              },
              "Sector Components/Pensions/P8000": "Sector Components/Pensions/P8000",
              "sedGVer": "4",
              "sedVer": "2",
              "sed": "${sedType.name}"
            }
        """.trimIndent()
    }

    protected fun createHendelseJson(sedType: SedType): String {
        return """
            {
              "id": 1869,
              "sedId": "P8000_b12e06dda2c7474b9998c7139c841646_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
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
              "navBruker": null
            }
        """.trimIndent()
    }

    private fun createGjenlevende(fnr: String?): String {
        return """
          ,
          "pensjon" : {
            "gjenlevende" : {
              "person" : {
                "statsborgerskap" : [ {
                  "land" : "DE"
                } ],
                "etternavn" : "Gjenlev",
                "fornavn" : "Lever",
                "kjoenn" : "M",
                "foedselsdato" : "1988-07-12"
                ${if (fnr != null) createPinJson(fnr) else ""}
              }
            }
          }
        """.trimIndent()
    }

    protected fun createSedP5000(fnr: String?, gfn: String? = null, eessiSaknr: String? = null): String {
        return """
    {
      "sed" : "P5000",
      "sedGVer" : "4",
      "sedVer" : "1",
      "nav" : {
        ${if (eessiSaknr != null) createEESSIsakJson(eessiSaknr) else ""}
        "bruker" : {
          "person" : {
            "statsborgerskap" : [ {
              "land" : "NO"
            } ],
            "etternavn" : "Død",
            "fornavn" : "Avdød",
            "kjoenn" : "M",
            "foedselsdato" : "1988-07-12"
            ${if (fnr != null) createPinJson(fnr) else ""}
          }
        }
      }
    ${if (gfn != null) createGjenlevende(gfn) else ""}
    }
    """.trimIndent()
    }

    protected fun mockAllDocumentsBuc(document: List<Triple<String, String, String>>): String {
        val sb = StringBuilder()
        sb.append("[").appendln()
        document.forEach {
            sb.append( singleActionDocument(it.first, it.second, it.third) )
                    .append(",")
        }
        val st = sb.toString()
        val s = st.substring(0, st.length - 1)
        return "$s \n]"
    }

    private fun singleActionDocument(documentid: String, documentType: String, status: String): String {
        return """{
        "id": "$documentid",
        "parentDocumentId": null,
        "type": "$documentType",
        "status": "$status",
        "creationDate": 1572005370040,
        "lastUpdate": 1572005370040,
        "displayName": "Forespørsel om informasjon",
        "participants": [
          {
            "role": "Sender",
            "organisation": {
              "address": {
                "country": "NO",
                "town": null,
                "street": null,
                "postalCode": null,
                "region": null
              },
              "activeSince": "2018-08-26T22:00:00.000+0000",
              "registryNumber": null,
              "acronym": "NAV ACCT 07",
              "countryCode": "NO",
              "contactMethods": null,
              "name": "NAV ACCEPTANCE TEST 07",
              "location": null,
              "assignedBUCs": null,
              "id": "NO:NAVAT07",
              "accessPoint": null
            },
            "selected": true
          },
          {
            "role": "Receiver",
            "organisation": {
              "address": {
                "country": "NO",
                "town": null,
                "street": null,
                "postalCode": null,
                "region": null
              },
              "activeSince": "2018-08-26T22:00:00.000+0000",
              "registryNumber": null,
              "acronym": "NAV ACCT 08",
              "countryCode": "NO",
              "contactMethods": null,
              "name": "NAV ACCEPTANCE TEST 08",
              "location": null,
              "assignedBUCs": null,
              "id": "NO:NAVAT08",
              "accessPoint": null
            },
            "selected": false
          }
        ],
        "attachments": [],
        "version": "1"
      }""".trimIndent()
    }




}
