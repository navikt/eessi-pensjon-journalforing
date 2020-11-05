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
import no.nav.eessi.pensjon.listeners.GyldigFunksjoner
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
import org.junit.jupiter.api.BeforeEach
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths

internal open class JournalforingTestBase {

    protected val euxKlient: EuxKlient = mockk()
    private val norg2Klient: Norg2Klient = mockk(relaxed = true)

    private val journalpostKlient: JournalpostKlient = mockk()

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

    private val aktoerregisterService: AktoerregisterService = mockk()
    protected val personV3Service: PersonV3Service = mockk()
    protected val diskresjonService: DiskresjonkodeHelper = spyk(DiskresjonkodeHelper(personV3Service, SedFnrSøk()))

    private val personidentifiseringService = PersonidentifiseringService(
            aktoerregisterService, personV3Service, diskresjonService, FnrHelper(), FdatoHelper()
    )

    protected val fagmodulKlient: FagmodulKlient = mockk()
    private val sedDokumentHelper = SedDokumentHelper(fagmodulKlient, euxKlient)
    protected val bestemSakOidcRestTemplate: RestTemplate = mockk()
    private val bestemSakKlient = BestemSakKlient(bestemSakOidcRestTemplate = bestemSakOidcRestTemplate)
    private val gyldigeFunksjoner: GyldigFunksjoner = mockk()

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

    protected fun initJournalPostRequestSlot(): Pair<CapturingSlot<OpprettJournalpostRequest>, OpprettJournalPostResponse> {
        val request = slot<OpprettJournalpostRequest>()

        val responseJson = String(Files.readAllBytes(Paths.get("src/test/resources/journalpost/opprettJournalpostResponse.json")))
        val journalpostResponse = mapJsonToAny(responseJson, typeRefs<OpprettJournalPostResponse>(), true)

        every { journalpostKlient.opprettJournalpost(capture(request), any()) } returns journalpostResponse

        return request to journalpostResponse
    }

    protected fun createAnnenPersonJson(fnr: String? = null, rolle: String? = "01"): String {
        return """
            {
                "person": {
                      ${if (rolle != null)  "\"rolle\" : \"$rolle\"," else ""}
                    "fornavn": "Annen",
                    "etternavn": "Person",
                    "kjoenn": "U",
                    "foedselsdato": "1985-05-07"
                }
            }
        """.trimIndent()
    }
    protected fun createSedJson(sedType: SedType, fnr: String? = null, annenPerson: String? = null): String {
        return """
            {
              "nav": {
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
                    "foedselsdato": "1988-07-12",
                    "pin": [
                      {
                        "land": "NO",
                        "identifikator": "$fnr"
                      }
                    ]
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
