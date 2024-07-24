package no.nav.eessi.pensjon.gcp

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.journalforing.LagretJournalpostMedSedInfo
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.*

open class GcpStorageServiceTest{

    companion object{
        fun lageGcpStorageMedLagretJP(SedHendelse: SedHendelse, lagretJournalPost: LagretJournalpostMedSedInfo, gcpStorage: Storage) {
            every { gcpStorage.list("journalB") } returns mockk<Page<Blob>>().apply {
                //list start
                every { iterateAll() } returns mockk<MutableIterable<Blob>>().apply {
                    every { iterator() } returns mockk<MutableIterator<Blob>>().apply {
                        every { hasNext() } returns true andThen false
                        every { next() } returns mockk<Blob>().apply {
                            //blob nivå
                            every { getName() } returns SedHendelse.rinaSakId
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
    }
}