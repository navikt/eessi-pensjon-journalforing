package no.nav.eessi.pensjon.journalforing

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.Journalpost
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OppdaterJPMedMottakerTest {

    private var safClient: SafClient = mockk()
    private var euxService: EuxService = mockk()
    private var journalpostKlient: JournalpostKlient = mockk()
    private var gcpStorageService: GcpStorageService = mockk()
    private lateinit var oppdaterJPMedMottaker: OppdaterJPMedMottaker

    @BeforeEach
    fun setUp() {
        oppdaterJPMedMottaker = OppdaterJPMedMottaker(safClient, euxService, journalpostKlient, gcpStorageService, "JournalpostIderTest.txt")

        every { euxService.hentDeltakereForBuc(any()) } returns deltakere()
        every { journalpostKlient.oppdaterJournalpostMedMottaker(any(), any()) } just Runs
        every { gcpStorageService.hentIndex() } returns null

        val jp = """
            {"journalpostId":"453976326","bruker":{"id":"2964001528817","type":"AKTOERID"},"tittel":"Utgående P6000 - Melding om vedtak","journalposttype":"U","journalstatus":"EKSPEDERT","tema":"PEN","behandlingstema":"ab0254","journalforendeEnhet":"4862","eksternReferanseId":"e05590fc-b93f-48a2-b7c9-2f838692fc68","tilleggsopplysninger":[{"nokkel":"eessi_pensjon_bucid","verdi":"1447240"}],"datoOpprettet":"2025-03-20T10:22:39"}
        """.trimIndent()
        every { safClient.hentJournalpostForJp(any())} returns mapJsonToAny<Journalpost>(jp)
//        every { safClient.hentJournalpostForJp(any())} returns journalPost andThen journalPost.copy(tilleggsopplysninger = listOf(mapOf("nokkel" to "nyVerdi"), mapOf("verdi" to "1447242")))

    }
    @AfterEach
    fun tearDown(){
        File("/tmp/journalpostIderSomGikkBra.txt").delete()
        File("/tmp/journalpostIderSomFeilet.txt").delete()
    }

    @Test
    fun `skal oppdatere alle som ligger `() {
        oppdaterJPMedMottaker.oppdatereHeleSulamitten()
        verify(exactly = 5) { journalpostKlient.oppdaterJournalpostMedMottaker(any(), avsenderMottker(deltakere().first().organisation!!)) }
    }

    @Test
    fun `skal hoppe over allerede lagret filer`() {
        OppdaterJPMedMottaker.JournalpostIdFilLager("/tmp/journalpostIderSomGikkBra.txt").leggTil("453976833")
        every { gcpStorageService.hentIndex() } returns "453976833"
        oppdaterJPMedMottaker.oppdatereHeleSulamitten()

        verify(exactly = 1) { euxService.hentDeltakereForBuc(any()) }
        verify(exactly = 3) { journalpostKlient.oppdaterJournalpostMedMottaker(any(), any()) }
    }

    fun avsenderMottker(organisation: Organisation) : String {
        return JournalpostResponse(avsenderMottaker = AvsenderMottaker(
            id = organisation.id,
            idType = IdType.UTL_ORG,
            navn = organisation.name,
            land = organisation.countryCode
        )).toJsonSkipEmpty()
    }

    private fun deltakere() = listOf(
        Participant(organisation = Organisation(countryCode = "DE", name = "Deutschland", id = "123456789")),
        Participant(organisation = Organisation(countryCode = "NO", name = "Norge", id = "987654321")),
        Participant(organisation = Organisation(countryCode = "FR", name = "Frankrike", id = "987654321")),
    )
}