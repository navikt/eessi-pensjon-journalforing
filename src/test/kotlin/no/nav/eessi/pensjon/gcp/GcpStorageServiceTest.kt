package no.nav.eessi.pensjon.gcp

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.MockKAdditionalAnswerScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

open class GcpStorageServiceTest {

    private lateinit var gcpStorageService: GcpStorageService

    //    private lateinit var rinaSakerService: RinaSakerService
    private val storage: Storage = mockk(relaxed = true)
    private val journalBucket = "test-bucket"
    lateinit var lagretJournalPost: LagretJournalpostMedSedInfo
    lateinit var sedMedBruker: SedHendelse
    lateinit var sedUtenBruker: SedHendelse

    @BeforeEach
    fun setUp() {
        sedMedBruker = SedHendelse.fromJson(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json")!!.readText())
        sedUtenBruker =
            SedHendelse.fromJson(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")!!.readText())
                .copy(
                    rinaSakId = sedMedBruker.rinaSakId
                )
        val lagretJournalpostRquest = opprettJournalpostRequest(bruker = null, enhet = Enhet.ID_OG_FORDELING, tema = Tema.UFORETRYGD, )
        lagretJournalPost = LagretJournalpostMedSedInfo(lagretJournalpostRquest, sedUtenBruker, HendelseType.SENDT)

        gcpStorageService = GcpStorageService("gjennyB", "journalB", storage)
        every { storage.get(BlobId.of("journalB", sedUtenBruker.rinaSakId)) } returns mockk {
            every { exists() } returns true
            every { getContent() } returns lagretJournalPost.toJson().toByteArray()
        }

    }

    @Test
    fun `skal returnere lagret journalpost`() {

        simulerGcpStorage(sedUtenBruker, lagretJournalPost, gcpStorage = storage)

        val result = gcpStorageService.hentGamleRinaSakerMedJPDetlajer(2)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals(
            result[0],
            lagretJournalPost.toJson())
    }

    companion object{
        fun simulerGcpStorage(SedHendelse: SedHendelse, lagretJournalPost: LagretJournalpostMedSedInfo, gcpStorage: Storage) {
            every { gcpStorage.list("journalB") } returns mockk<Page<Blob>>().apply {
                //list start
                every { iterateAll() } returns mockk<MutableIterable<Blob>>().apply {
                    every { iterator() } returns mockk<MutableIterator<Blob>>().apply {
                        every { hasNext() } returns true andThen false
                        every { next() } returns mockk<Blob>().apply {
                            //blob nivå
                            every { getName() } returns SedHendelse.rinaSakId
                            every { createTimeOffsetDateTime } returns OffsetDateTime.now().minusDays(12)
                            every { blobId } returns mockk(relaxed = true)
                            every { getContent() } returns lagretJournalPost.toJson().toByteArray()
                            every { getValues() } returns mockk<MutableIterable<Blob>>().apply {
                                //liste funksjonalitet for values innenfor en blob
                                every { iterator() } returns mockk<MutableIterator<Blob>>().apply {
                                    every { hasNext() } returns true andThen false
                                    every { next() } returns mockk<Blob>().apply {
                                        // metainfo for enkelt objekt på values
                                        every { getName() } returns SedHendelse.rinaSakId
                                        every { getContent() } returns lagretJournalPost.toJson().toByteArray()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun opprettJournalpostRequest(bruker: Bruker?, enhet:Enhet?, tema: Tema?) = OpprettJournalpostRequest(
            AvsenderMottaker(land = "GB"),
            Behandlingstema.ALDERSPENSJON,
            bruker = bruker,
            "[]",
            enhet,
            JournalpostType.INNGAAENDE,
            Sak("FAGSAK", "11111", "PEN"),
            tema = tema ?: Tema.OMSTILLING,
            emptyList(),
            "tittel på sak"
        )
    }
}