package no.nav.eessi.pensjon.integrasjonstest.sendt

import ch.qos.logback.classic.spi.ILoggingEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
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

@Disabled
@SpringBootTest(classes = [P_BUC_01MedGyldigVedleggTest.TestConfig::class],  value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(controlledShutdown = true, partitions = 1, topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC], brokerProperties = ["log.dir=out/embedded-kafkasendt"])
class P_BUC_01MedGyldigVedleggTest : SendtIntegrationBase() {

    @Autowired
    lateinit var personService: PersonService

    @Autowired
    lateinit var euxService: EuxService


    @TestConfiguration
    class TestConfig {
        @Bean
        fun personService(): PersonService {
            return mockk(relaxed = true) {
                every { initMetrics() } just Runs
            }
        }

        @Bean
        fun euxService(): EuxService {
            return spyk(EuxService(mockk(relaxed = true), MetricsHelper(SimpleMeterRegistry())))
        }
    }

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {
        initAndRunContainer()
        clearAllMocks()
    }

    override fun initExtraMock() {
        // Mock PDL Person
        every { personService.hentPerson(NorskIdent("09035225916")) }
            .answers { PersonMock.createWith(fnr = "09035225916", aktoerId = AktoerId("1000101917358")) }

        every { personService.harAdressebeskyttelse(any(), any()) }
            .answers { false }

        // Mock EUX buc
        every { euxService.hentBuc(any()) }
            .answers {
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                )
            }

        // Mock EUX vedlegg PDF
        every { euxService.hentAlleDokumentfiler("147729", "b12e06dda2c7474b9998c7139c841646") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        // Mock EUX Service (SEDer)
        every { euxService.hentSed(any(), "44cb68f89a2f4e748934fb4722721018") }
            .answers { opprettSED("/sed/P2000-NAV.json", SED::class.java) }

    }

    override fun produserSedHendelser(sedSendtProducerTemplate: KafkaTemplate<Int, String>) {
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText())
    }

    override fun verifiser() {
        assertEquals(6, sedListener.getLatch().count, "Alle meldinger har ikke blitt konsumert")
        verify(exactly = 1) { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxService.hentBuc(any()) }
        verify(exactly = 1) { euxService.hentAlleDokumentfiler("147729", "b12e06dda2c7474b9998c7139c841646") }
        verify(exactly = 1) { euxService.hentSed("147729", "44cb68f89a2f4e748934fb4722721018") }

        val logsList: List<ILoggingEvent> = listAppender.list

        val oppgavemelding = """
            Opprette oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding: {
              "sedType" : "P2000",
              "journalpostId" : "429434378",
              "tildeltEnhetsnr" : "9999",
              "aktoerId" : "1000101917358",
              "rinaSakId" : "147729",
              "hendelseType" : "SENDT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING"
            }
        """.trimIndent()
        assertEquals(
            oppgavemelding, logsList.find { message -> message.message.contains("Opprette oppgave melding på kafka") }?.message
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
