package no.nav.eessi.pensjon.integrasjonstest.sendt

import ch.qos.logback.classic.spi.ILoggingEvent
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.eessi.pensjon.buc.EuxKlient
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.json.toJson
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
    classes = [P_BUC_03_P2200Test.TestConfig::class],
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
class P_BUC_03_P2200Test : SendtIntegrationBase() {
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
        sedSendtProducerTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_03_P2200.json").readText())
    }

    override fun initExtraMock() {
        // Mock PDL Person
        every { personService.hentPerson(NorskIdent("09035225916")) }
            .answers { PersonMock.createWith(fnr = "09035225916", aktoerId = AktoerId("1000101917358")) }

        every { personService.harAdressebeskyttelse(any(), any()) }
            .answers { false }

        // Mock EUX buc
        every { euxKlient.hentBuc(any()) }
            .answers {
                Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                )
            }

        // Mock EUX vedlegg PDF
        every { euxKlient.hentAlleDokumentfiler("147729", "b12e06dda2c7474b9998c7139c841646") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        // Mock EUX Service (SEDer)
        every { euxKlient.hentSedJson(any(), "44cb68f89a2f4e748934fb4722721018") }
            .answers { opprettSED("/sed/P2000-NAV.json", SED::class.java).toJson() }

        every { euxKlient.hentAlleDokumentfiler("148161", "f899bf659ff04d20bc8b978b186f1ecc") }
            .answers { opprettSedDokument("/pdf/pdfResponseUtenVedlegg.json") }

        every { euxKlient.hentSedJson("148161", "f899bf659ff04d20bc8b978b186f1ecc", ) }
            .answers { opprettSED("/eux/SedResponseP2000.json", SED::class.java).toJson() }
    }


    override fun verifiser() {
        Assertions.assertEquals(6, sedListener.getLatch().count, "Alle meldinger har ikke blitt konsumert")
        verify(exactly = 1) { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler("148161", "f899bf659ff04d20bc8b978b186f1ecc") }

        val logsList: List<ILoggingEvent> = listAppender.list

        val oppgavemelding = """
            Opprette oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding: {
              "sedType" : "P2200",
              "journalpostId" : "429434378",
              "tildeltEnhetsnr" : "4303",
              "aktoerId" : null,
              "rinaSakId" : "148161",
              "hendelseType" : "SENDT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING"
            }
        """.trimIndent()
        val messageSent = logsList.find { message -> message.message.contains("Opprette oppgave melding på kafka") }?.message
        Assertions.assertEquals(messageSent, oppgavemelding)

    }

    override fun moreMockServer(mockServer: ClientAndServer) {
        CustomMockServer(mockServer)
            .medJournalforing()
            .medNorg2Tjeneste()
            .medOppdatertDistroTjeneste()
            .mockBestemSak()
    }
}
