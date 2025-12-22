package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.HentSakService
import no.nav.eessi.pensjon.journalforing.Sak
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.event.Level
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.random.Random

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class, IntegrasjonsBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka
@Disabled
internal class SedMottattIntegrationTest : IntegrasjonsBase() {

    init {
        if(System.getProperty("mockServerport") == null){
            val port = System.getProperty("mockServerport")?.toInt() ?: PortFactory.findFreePort()
            mockServer = ClientAndServer(Configuration().apply { logLevel(Level.ERROR) }, port)
            System.setProperty("mockServerport", port.toString())
        }
    }

    @MockkBean(relaxed = true)
    lateinit var gcpStorageService: GcpStorageService

    private var hentSakService: HentSakService = mockk()

    @BeforeEach
    fun setUp() {
        listOf("147729", "147666", "7477291").forEach {
            every { hentSakService.hentSak(it) } returns Sak(
                fagsakid = "1234567".plus(Random.Default.nextInt(10, 99)),
                sakstype = "FAGSAK",
                fagsaksystem = "PP01"
            )
        }
    }

    @Test
    fun `H070-NAV for en H_BUC_07 skal journalfores`() {
        val bucId = "147729"
        val journalpostId = "429434388"
        val sedId1 = "44cb68f89a2f4e748934fb4722721018"
        val sedId2 = "9498fc46933548518712e4a1d5133113"
        setupMockServer(
            bucId = bucId, journalpostId = journalpostId, responses = listOf(
                "/buc/$bucId/sed/$sedId1" to "/sed/H070-NAV.json",
                "/buc/$bucId/sed/$sedId1/filer" to "/pdf/pdfResponseUtenVedlegg.json",
                "/buc/$bucId/sed/$sedId2/filer" to "/pdf/pdfResponseUtenVedlegg.json"
            )
        )
        startJornalforingForMottatt("/eux/hendelser/H_BUC_07_H070.json")
        verifyOppgave(journalpostId, "MOTTATT", "H070", "4303")
    }

    @Test
    fun `P2000 skal routes to 4303`() {
        val bucId = "147729"
        val journalpostId = "429434378"
        val sedId1 = "44cb68f89a2f4e748934fb4722721018"
        val sedId2 = "b12e06dda2c7474b9998c7139c841646"
        setupMockServer(
            bucId = bucId, journalpostId = journalpostId, responses = listOf(
                "/buc/$bucId/sed/$sedId1" to "/sed/P2000-ugyldigFNR-NAV.json",
                "/buc/$bucId/sed/$sedId2/filer" to "/pdf/pdfResponseUtenVedlegg.json"
            )
        )
        startJornalforingForMottatt("/eux/hendelser/P_BUC_01_P2000.json")
        verifyOppgave(journalpostId, "MOTTATT", "P2000", "4303")
    }

    @Test
    fun `P2000 med ugyldig FNR skal routes til 4303`() {
        val bucId = "7477291"
        val journalpostId = "429434388"
        val sedId1 = "b12e06dda2c7474b9998c7139c841646fffx"
        setupMockServer(
            bucId = bucId,
            journalpostId = journalpostId,
            bucDocList = "/fagmodul/alldocuments_ugyldigFNR_ids.json",
            responses = listOf(
                "/buc/$bucId/sed/$sedId1" to "/sed/P2000-ugyldigFNR-NAV.json",
                "/buc/$bucId/sed/$sedId1/filer" to "/pdf/pdfResponseUtenVedlegg.json"
            )
        )
        startJornalforingForMottatt("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")
        verifyOppgave(journalpostId, "MOTTATT", "P2000", "4303")
    }

    @Test
    fun `Pensjon SED (P2000) med ugyldig vedlegg skal routes til 9999`() {
        val bucId = "147666"
        val journalpostId = "429434378"
        val sedId1 = "44cb68f89a2f4e748934fb4722721018"
        val sedId2 = "b12e06dda2c7474b9998c7139c666666"
        setupMockServer(
            bucId = bucId,
            journalpostId = journalpostId,
            bucDocList = "/fagmodul/alldocumentsids.json",
            responses = listOf(
                "/buc/147666/sed/$sedId1" to "/sed/P2000-NAV.json",
                "/buc/147666/sed/$sedId2/filer" to "/pdf/pdfResponseMedUgyldigVedlegg.json"
            )
        )
        startJornalforingForMottatt("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")
        verifyOppgave(journalpostId, "MOTTATT", "P2000", "4303")
    }
}
