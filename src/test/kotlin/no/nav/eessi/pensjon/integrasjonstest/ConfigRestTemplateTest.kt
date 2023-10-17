package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.config.RestTemplateConfig
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.document.MimeType
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.navansatt.NavansattKlient
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.listeners.SedSendtListener
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate

@SpringBootTest( classes = [EessiPensjonJournalforingTestApplication::class, RestTemplateConfig::class, ConfigRestTemplateTest.TestConfig::class, IntegrasjonsTestConfig::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC]
)
@MockkBeans(
    MockkBean(name = "bestemSakOidcRestTemplate", classes = [RestTemplate::class]),
)
internal class ConfigRestTemplateTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var sedSendtListener: SedSendtListener

    @MockkBean
    private lateinit var personService: PersonService

    @MockkBean
    private lateinit var bestemSakKlient: BestemSakKlient

    @MockkBean
    private lateinit var journalpostKlient: JournalpostKlient

    @MockkBean(relaxed = true)
    private lateinit var navansattKlient: NavansattKlient

    @BeforeEach
    fun setup() {
        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        every { personService.sokPerson(any()) } returns setOf(
            IdentInformasjon(
                JournalforingTestBase.FNR_VOKSEN_UNDER_62,
                IdentGruppe.FOLKEREGISTERIDENT
            ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
        )
        every { personService.hentPerson(NorskIdent(JournalforingTestBase.FNR_VOKSEN_UNDER_62)) } returns
                JournalforingTestBase().createBrukerWith(
                    JournalforingTestBase.FNR_VOKSEN_UNDER_62,
                    aktorId = JournalforingTestBase.AKTOER_ID
                )
        every { personService.hentPerson(NorskIdent(JournalforingTestBase.FNR_VOKSEN_2)) } returns
                JournalforingTestBase().createBrukerWith(
                    JournalforingTestBase.FNR_VOKSEN_2,
                    aktorId = JournalforingTestBase.AKTOER_ID
                )
        every { bestemSakKlient.kallBestemSak(any()) } returns mockk(relaxed = true)
    }


    /**
     * Jackson har en begrensning på 20MB for dokumenter. Dette er en test for å verifisere at RestTemplateConfig
     * kan håndere filer som er større enn dette
     */
    @Test
    fun `Konfigurasjon skal håndtere en pdf med dokumenter større enn 20b`() {
        val requestSlot = slot<OpprettJournalpostRequest>()

        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any()) } returns mockk(relaxed = true)

        sedSendtListener.consumeSedSendt(
            javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")!!.readText(),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        //sjekker at denne går gjennom og at det lages en oppgave
        assertEquals(Tema.PENSJON, requestSlot.captured.tema)
        assertEquals(Enhet.ID_OG_FORDELING, requestSlot.captured.journalfoerendeEnhet)
    }

    @TestConfiguration
    class TestConfig {

        @Bean
        @Primary
        fun EuxKlientLib(): EuxKlientLib = EuxKlientLib(euxOAuthRestTemplate())

        @Bean
        fun navansattRestTemplate(): RestTemplate = mockk(relaxed = true)

        fun euxOAuthRestTemplate(): RestTemplate {
            return RestTemplateBuilder().build().apply {
                val mvc = MockRestServiceServer.bindTo(this).build()
                val sedDokumentfiler = SedDokumentfiler(
                    SedVedlegg("", MimeType.PDF, ""),
                    listOf(SedVedlegg("", MimeType.PDF, javaClass.getResource("/25_mb_randomString.txt")!!.readText()))
                )

                mvc.expect(MockRestRequestMatchers.requestTo("/buc/147666"))
                    .andRespond(
                        MockRestResponseCreators
                            .withSuccess(
                                Buc(
                                    id = "147666",
                                    participants = emptyList<Participant>(),
                                    documents = mapJsonToAny(
                                        javaClass.getResource("/fagmodul/alldocumentsids.json")!!.readText()
                                    )
                                ).toJson(), MediaType.APPLICATION_JSON
                            )
                    )
                mvc.expect(MockRestRequestMatchers.requestTo("/buc/147666/sed/44cb68f89a2f4e748934fb4722721018"))
                    .andRespond(
                        MockRestResponseCreators
                            .withSuccess(
                                javaClass.getResource("/sed/P2000-NAV.json")!!.readText(),
                                MediaType.APPLICATION_JSON
                            )
                    )
                mvc.expect(MockRestRequestMatchers.requestTo("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer"))
                    .andRespond(
                        MockRestResponseCreators.withSuccess(
                            sedDokumentfiler.toJson(),
                            MediaType.APPLICATION_JSON
                        )
                    )
            }
        }
    }
}
