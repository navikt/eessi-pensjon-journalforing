package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach

open class GcpStorageServiceTest {

    private lateinit var gcpStorageService: GcpStorageService
    private val storage: Storage = mockk()

    lateinit var lagretJournalPost: JournalpostMedSedInfo
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
        lagretJournalPost = JournalpostMedSedInfo(lagretJournalpostRquest, sedUtenBruker, HendelseType.SENDT)

        every { storage.get(BlobId.of("journalB", sedUtenBruker.rinaSakId)) } returns mockk {
            every { exists() } returns true
            every { getContent() } returns lagretJournalPost.toJson().toByteArray()
        }
        listOf("gjennyB", "journalB").forEach { every { storage.get(it, *emptyArray<Storage.BucketGetOption>()) } returns mockk(relaxed = true)}
        gcpStorageService = GcpStorageService("gjennyB", storage)

    }

    companion object{
        fun opprettJournalpostRequest(bruker: Bruker?, enhet:Enhet?, tema: Tema?, behandlingstema: Behandlingstema? = Behandlingstema.ALDERSPENSJON) = OpprettJournalpostRequest(
            AvsenderMottaker(land = "GB"),
            behandlingstema,
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