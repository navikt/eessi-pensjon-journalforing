package no.nav.eessi.pensjon.integrasjonstest.sendt

import ch.qos.logback.classic.spi.ILoggingEvent
import io.mockk.*
import no.nav.eessi.pensjon.buc.EuxKlient
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1"

@SpringBootTest(
    classes = [P_BUC_01P2000MedUgyldigFNRTest.TestConfig::class],
    value = ["SPRING_PROFILES_ACTIVE", "integrationtest"]
)
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    partitions = 1,
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC],
    brokerProperties = ["log.dir=out/embedded-kafkasendt"]
)
class P_BUC_01P2000MedUgyldigFNRTest : SendtIntegrationBase() {
    @Autowired
    lateinit var personService: PersonService

    @Autowired
    lateinit var euxKlient: EuxKlient


    @TestConfiguration
    class TestConfig {
        @Bean
        fun personService(): PersonService {
            return mockk(relaxed = true) {
                every { initMetrics() } just Runs
            }
        }

        @Bean
        fun euxKlient(): EuxKlient {
            return spyk(EuxKlient(mockk(relaxed = true)))
        }
    }

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {
        initAndRunContainer()
        clearAllMocks()
    }

    override fun produserSedHendelser(sedSendtProducerTemplate: KafkaTemplate<Int, String>) {
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json").readText())
    }

    override fun initExtraMock() {
        // Mock PDL Person
        every { personService.hentPerson(NorskIdent("09035225916")) }
            .answers { PersonMock.createWith(fnr = "09035225916", aktoerId = AktoerId("1000101917358")) }

        every { personService.harAdressebeskyttelse(any(), any()) }
            .answers { false }

        // mock ugyldig fnr
        every { euxKlient.hentBuc("7477291") }
            .answers {
                Buc(
                    id ="7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocuments_ugyldigFNR_ids.json")
                )
            }

        // Mock EUX buc
        every { euxKlient.hentBuc(any()) }
            .answers {
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                )
            }

        // General mocks: Mock EUX vedlegg PDF
        every { euxKlient.hentAlleDokumentfiler("147729", "b12e06dda2c7474b9998c7139c841646") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        // General mocks: Mock EUX Service (SEDer)
        every { euxKlient.hentSedJson(any(), "44cb68f89a2f4e748934fb4722721018") }
            .answers {  javaClass.getResource("/sed/P2000-NAV.json").readText() }

        //Specific mocks
        every { euxKlient.hentAlleDokumentfiler("7477291", "b12e06dda2c7474b9998c7139c841646fffx") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxKlient.hentSedJson("7477291", "b12e06dda2c7474b9998c7139c841646fffx", ) }
            .answers { javaClass.getResource("/sed/P2000-ugyldigFNR-NAV.json").readText() }
    }

    override fun verifiser() {
        verify(atLeast = 1) { euxKlient.hentAlleDokumentfiler("7477291", "b12e06dda2c7474b9998c7139c841646fffx") }
        verify(exactly = 1) { personService.hentPerson(any<Ident<*>>()) }

        val logsList: List<ILoggingEvent> = listAppender.list

        val oppgavemelding = """
            Opprette oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding: {
              "sedType" : "P2000",
              "journalpostId" : "429434378",
              "tildeltEnhetsnr" : "4303",
              "aktoerId" : null,
              "rinaSakId" : "7477291",
              "hendelseType" : "SENDT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING"
            }
        """.trimIndent()
        Assertions.assertEquals(
            oppgavemelding,
            logsList.find { message -> message.message.contains("Opprette oppgave melding på kafka") }?.message
        )
    }

    override fun moreMockServer(mockServer: ClientAndServer) {
        CustomMockServer(mockServer)
            .medJournalforing()
            .medNorg2Tjeneste()
            .medOppdatertDistroTjeneste()
            .mockBestemSak()
    }
}
