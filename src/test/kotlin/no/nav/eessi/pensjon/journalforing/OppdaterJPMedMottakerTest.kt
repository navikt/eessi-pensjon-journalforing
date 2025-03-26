package no.nav.eessi.pensjon.journalforing

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import org.junit.jupiter.api.Test

class OppdaterJPMedMottakerTest {

    val safClient: SafClient = mockk(relaxed = true)
    val euxService: EuxService = mockk()
    val journalpostKlient: JournalpostKlient = mockk()


    @Test
    fun `test oppdaterJournalpostMedMottaker`() {
        val oppdaterJPMedMottaker = OppdaterJPMedMottaker(safClient, euxService, journalpostKlient)
        oppdaterJPMedMottaker.oppdatereHeleSulamitten()

    }
}