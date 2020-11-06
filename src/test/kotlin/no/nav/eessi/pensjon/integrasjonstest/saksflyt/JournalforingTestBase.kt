package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.handler.OppgaveHandler
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
import no.nav.eessi.pensjon.listeners.GyldigeFunksjonerToggleNonProd
import no.nav.eessi.pensjon.listeners.GyldigeHendelser
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.Norg2Klient
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSøk
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bostedsadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
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
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.RestTemplate

internal open class JournalforingTestBase {

    protected val euxKlient: EuxKlient = mockk()
    private val norg2Klient: Norg2Klient = mockk(relaxed = true)

    protected val journalpostKlient: JournalpostKlient = mockk(relaxed = true)

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
    protected val diskresjonService: DiskresjonkodeHelper = spyk(DiskresjonkodeHelper(personV3Service, SedFnrSøk()))

    private val personidentifiseringService = PersonidentifiseringService(
            aktoerregisterService, personV3Service, diskresjonService, FnrHelper(), FdatoHelper()
    )

    protected val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    private val sedDokumentHelper = SedDokumentHelper(fagmodulKlient, euxKlient)
    protected val bestemSakOidcRestTemplate: RestTemplate = mockk()
    private val bestemSakKlient = BestemSakKlient(bestemSakOidcRestTemplate = bestemSakOidcRestTemplate)
    private val gyldigeFunksjoner = GyldigeFunksjonerToggleNonProd()

    protected val listener: SedListener = SedListener(
            journalforingService = journalforingService,
            personidentifiseringService = personidentifiseringService,
            sedDokumentHelper = sedDokumentHelper,
            gyldigeHendelser = GyldigeHendelser(),
            bestemSakKlient = bestemSakKlient,
            gyldigeFunksjoner = gyldigeFunksjoner,
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

    protected fun createBrukerWith(fnr: String?, fornavn: String = "Fornavn", etternavn: String = "Etternavn", land: String? = "NOR", geo: String = "1234"): Bruker {
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
    }

    private fun journalpostResponseJson(ferdigstilt: Boolean? = false): String {
        return """
            {
              "journalpostId": "429434378",
              "journalstatus": "M",
              "melding": "null",
              "journalpostferdigstilt": $ferdigstilt,
              "dokumenter": [
                {
                  "dokumentInfoId": "453867272"
                }
              ]
            }
        """.trimIndent()

    }

    protected fun initJournalPostRequestSlot(ferdigstilt: Boolean? = false): Pair<CapturingSlot<OpprettJournalpostRequest>, OpprettJournalPostResponse> {
        val request = slot<OpprettJournalpostRequest>()
        val responseJson = journalpostResponseJson(ferdigstilt)
        val journalpostResponse = mapJsonToAny(responseJson, typeRefs<OpprettJournalPostResponse>(), true)

        every { journalpostKlient.opprettJournalpost(capture(request), any()) } returns journalpostResponse

        return request to journalpostResponse
    }

    protected fun createAnnenPersonJson(fnr: String? = null, fdato: String = "1985-05-07" , rolle: String? = "01"): String {
        return """
            {
                "person": {
                      ${if (rolle != null)  "\"rolle\" : \"$rolle\"," else ""}
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

    protected fun createSedJson(sedType: SedType, fnr: String? = null, annenPerson: String? = null, eessiSaknr: String? = null): String {

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
                    "foedselsdato": "1988-07-12"
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
}
