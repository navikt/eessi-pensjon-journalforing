package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase.Companion.FNR_VOKSEN_UNDER_62
import no.nav.eessi.pensjon.journalforing.OppdaterJPMedMottaker
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class, SedSendtP9000IntegrationTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC]
)
internal class SedSendtP9000IntegrationTest : IntegrasjonsBase() {
    @MockkBean
    private lateinit var personService: PersonService

    @MockkBean
    private lateinit var oppdaterJPMedMottaker: OppdaterJPMedMottaker

    @MockkBean(relaxed = true)
    private lateinit var gcpStorageService: GcpStorageService

    init {
        System.getProperty("mockServerport") ?: run {
            mockServer = ClientAndServer(Configuration().logLevel(Level.ERROR), PortFactory.findFreePort())
                .also {
                    System.setProperty("mockServerport", it.localPort.toString())
                }
        }
    }

    @BeforeEach
    fun setupTest(){
        every { gcpStorageService.hentFraGjenny(any())} returns null
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun euxRestTemplate(): RestTemplate = IntegrasjonsTestConfig().mockedRestTemplate()

        @Bean
        fun euxKlientLib(): EuxKlientLib = EuxKlientLib(euxRestTemplate())

        @Bean
        fun safClient(): SafClient = SafClient(IntegrasjonsTestConfig().mockedRestTemplate())
    }

    @Test
    fun `Skal finne fødselsdato for 3 personer gitt en forsikret person i P9000 og to gjenlevende (P8000) `() {
        every { personService.harAdressebeskyttelse(any()) } returns false
        every { personService.sokPerson(any()) } returns setOf(
            IdentInformasjon(
                FNR_VOKSEN_UNDER_62,
                IdentGruppe.FOLKEREGISTERIDENT
            ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
        )
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN_UNDER_62)) } returns JournalforingTestBase()
            .createBrukerWith(FNR_VOKSEN_UNDER_62,aktorId = JournalforingTestBase.AKTOER_ID)

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
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/30000000003", HttpMethod.GET,"/sed/p9000/forsikretMedToEtterlatte/p9000.json")
            .mockHttpRequestWithResponseFromFile("/buc/148161/sed/30000000003/filer", HttpMethod.GET, "/pdf/pdfResponseMedTomtVedlegg.json")

        meldingForSendtListener( "/eux/hendelser/P_BUC_05_P9000.json")

        assertTrue(isMessageInlog("Fant fødselsdato i P8000, fdato: 2005-03-29"))
        assertTrue(isMessageInlog("Fant fødselsdato i P8000, fdato: 2007-06-19"))
        assertTrue(isMessageInlog("Fant fødselsdato i P9000, fdato: 1971-06-11"))
    }
}
