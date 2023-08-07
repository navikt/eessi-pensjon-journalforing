package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase.Companion.FNR_VOKSEN
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class], value = ["SPRING_PROFILES_ACTIVE"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC]
)
internal class SedSendtP9000IntegrationTest : IntegrasjonsBase() {


    @MockkBean
    private lateinit var personService: PersonService

    init {
        if (System.getProperty("mockServerport") == null) {
            mockServer = ClientAndServer(Configuration().apply {
                logLevel(Level.ERROR)
            }, PortFactory.findFreePort())
                .also {
                    System.setProperty("mockServerport", it.localPort.toString())
                }
        }
    }

    @Test
    fun `Gitt en forsikret person i P9000 og to gjenlevende, to P8000 med to forskjellige etterlatte, så skal vi finne riktig etterlatte`() {
        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        every { personService.sokPerson(any()) } returns setOf(
            IdentInformasjon(
                FNR_VOKSEN,
                IdentGruppe.FOLKEREGISTERIDENT
            ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
        )
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns JournalforingTestBase()
            .createBrukerWith(FNR_VOKSEN,aktorId = JournalforingTestBase.AKTOER_ID)

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .mockPensjonsinformasjon()
            .mockHttpRequestWithResponseFromJson(
                "/buc/148161", HttpMethod.GET, Buc(
                    id = "148161",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids_P_BUC_05_multiP9000.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/10000000001", HttpMethod.GET,"/sed/p9000/forsikretMedToEtterlatte/p8000_BARN1.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/20000000002", HttpMethod.GET,"/sed/p9000/forsikretMedToEtterlatte/p8000_BARN2.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/30000000003",HttpMethod.GET,"/sed/p9000/forsikretMedToEtterlatte/p9000.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/30000000003/filer", HttpMethod.GET, "/pdf/pdfResponseMedTomtVedlegg.json")

        meldingForSendtListener( "/eux/hendelser/P_BUC_05_P9000.json")

        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P9000")
            .medtildeltEnhetsnr("4862")
            .medAktorId("0123456789000")

        //ser at den feiler pga manglende saksinformasjon og ikke før
        assertTrue(isMessageInlog("Journalpost enhet: ID_OG_FORDELING rutes til -> Saksbehandlende enhet: NFP_UTLAND_AALESUND"))
        //TODO: Vurdere om saksbehandling burde være en del av testen
    }
}
