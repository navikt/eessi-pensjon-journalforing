package no.nav.eessi.pensjon.journalforing

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GcpStorageServiceTest
import no.nav.eessi.pensjon.gcp.GcpStorageTestHelper
import no.nav.eessi.pensjon.journalforing.JournalforingServiceBase.Companion.identifisertPersonPDL
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate

private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
private const val AKTOERID = "12078945602"

class VurderBrukerInfoTest {

    private lateinit var safClient: SafClient
    private lateinit var gcpStorageService: GcpStorageService
    private lateinit var journalpostService: JournalpostService
    private lateinit var oppgaveHandler: OppgaveHandler
    private lateinit var vurderBrukerInfo: VurderBrukerInfo

    private var journalpostKlient: JournalpostKlient = mockk()
    private val lagretJournalpostRquest = GcpStorageServiceTest.opprettJournalpostRequest(bruker = null, enhet = Enhet.ID_OG_FORDELING, tema = Tema.UFORETRYGD, )
    private val dokumentId = "222222"
    lateinit var sedUtenBruker: SedHendelse
    lateinit var sedMedBruker: SedHendelse

    val identifisertPerson = identifisertPersonPDL(
        AKTOERID,
        SEDPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = dokumentId),
        "NOR"
    )
    private val storage: Storage = mockk(relaxed = true)
    lateinit var lagretJournalPost: JournalpostMedSedInfo

    @BeforeEach
    fun setUp() {
        safClient = mockk()
        gcpStorageService = GcpStorageService("gjennyB", "journalB", storage)
        journalpostService = spyk(JournalpostService(journalpostKlient))
        oppgaveHandler = spyk(
            OppgaveHandler(
                mockk<KafkaTemplate<String, String>>(relaxed = true).apply {
                    every { sendDefault(any(), any()).get() } returns mockk(relaxed = true) })
        )

        sedMedBruker = SedHendelse.fromJson(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json")!!.readText())
        sedUtenBruker = SedHendelse.fromJson(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")!!.readText()).copy (
            rinaSakId = sedMedBruker.rinaSakId
        )

        vurderBrukerInfo = spyk(
            VurderBrukerInfo(
                gcpStorageService,
                journalpostService,
                oppgaveHandler,
                MetricsHelper.ForTest()
            ))

        lagretJournalPost = JournalpostMedSedInfo(lagretJournalpostRquest, sedUtenBruker, HendelseType.SENDT)

        every { storage.get(BlobId.of("journalB", sedUtenBruker.rinaSakId)) } returns  mockk {
            every { exists() } returns true
            every { getContent() } returns lagretJournalPost.toJson().toByteArray()
        }
    }

    @Test
    fun `journalpost med bruker skal hente lageret jp uten bruker og opprette jp samt oppgave`() {
        val rinaID = "147729"
        val blobId = mockk<BlobId>(relaxed = true)

        every { journalpostKlient.opprettJournalpost(any(), any(), any()) } returns mockk<OpprettJournalPostResponse>().apply {
            every { journalpostId } returns "1111"
            every { journalstatus } returns "UNDER_ARBEID"
        }
        val bruker = createTestBruker("121280334444")
        val journalPostMedBruker = GcpStorageServiceTest.opprettJournalpostRequest(bruker, Enhet.UFORE_UTLAND, Tema.PENSJON)

        GcpStorageTestHelper.simulerGcpStorage(sedUtenBruker, listOf(Pair(lagretJournalPost, blobId)), gcpStorage = storage)

        vurderBrukerInfo.finnLagretSedUtenBrukerForRinaNr(journalPostMedBruker, sedMedBruker, identifisertPerson, bruker, "5555")

        verify {
            oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(mapJsonToAny("""
            {
              "sedType" : "P2000",
              "journalpostId" : "1111",
              "tildeltEnhetsnr" : "${journalPostMedBruker.journalfoerendeEnhet?.enhetsNr}",
              "aktoerId" : "${identifisertPerson.aktoerId}",
              "rinaSakId" : $rinaID,
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "${journalPostMedBruker.tema.kode}"
            }
        """.trimIndent())) }
        verify { journalpostService.sendJournalPost(eq(lagretJournalpostRquest.copy(
            tema = journalPostMedBruker.tema,
            journalfoerendeEnhet = journalPostMedBruker.journalfoerendeEnhet,
            bruker = journalPostMedBruker.bruker
        )), lagretJournalPost.sedHendelse, any(), any()) }
        verify(exactly = 1) { gcpStorageService.slettJournalpostDetaljer(BlobId.of("journalB", sedUtenBruker.rinaSakId)) }
    }


    fun createTestBruker(
        id: String? = "defaultId"
    ): Bruker {
        return Bruker(
            id = id!!
        )
    }
}