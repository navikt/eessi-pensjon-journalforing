package no.nav.eessi.pensjon.gcp

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.journalforing.JournalpostMedSedInfo
import no.nav.eessi.pensjon.utils.toJson
import java.time.OffsetDateTime

class GcpStorageTestHelper {

        companion object {
            fun simulerGcpStorage(
                sedHendelse: SedHendelse,
                lagretJournalPost: List<Pair<JournalpostMedSedInfo, BlobId>>,
                gcpStorage: Storage
            ) {
                val blob = createMockBlob(sedHendelse, lagretJournalPost)
                val page = createMockPage(blob, sedHendelse, lagretJournalPost.first().first)

                every { gcpStorage.list("journalB") } returns page
            }

            private fun createMockBlob(
                sedHendelse: SedHendelse,
                lagretJournalPost: List<Pair<JournalpostMedSedInfo, BlobId>>
            ): Blob {
                return mockk<Blob>().apply {
                    every { name } returns sedHendelse.rinaSakId
                    every { createTimeOffsetDateTime } returns OffsetDateTime.now().minusDays(12)
                    every { blobId } returns mockk(relaxed = true)
                    every { getContent() } returns lagretJournalPost.toJson().toByteArray()
                }
            }

            private fun createMockPage(blob: Blob, sedHendelse: SedHendelse, lagretJournalPost: JournalpostMedSedInfo): Page<Blob> {
                return mockk<Page<Blob>>().apply {
                    every { iterateAll() } returns mutableListOf(blob)
                    every { values } returns mockk<MutableIterable<Blob>>().apply {
                        every { iterator() } returns mockk<MutableIterator<Blob>>().apply {
                            every { hasNext() } returns true andThen false
                            every { next() } returns mockk<Blob>().apply {
                                every { name } returns sedHendelse.rinaSakId
                                every { getContent() } returns lagretJournalPost.toJson().toByteArray()
                            }
                        }
                    }
                }
            }
        }

}
