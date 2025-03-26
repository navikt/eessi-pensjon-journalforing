package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.saf.Journalpost
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Test

class OppdaterJPMedMottakerTest {

    val safClient: SafClient = mockk(relaxed = true)
    val euxService: EuxService = mockk()
    val journalpostKlient: JournalpostKlient = mockk()


    @Test
    fun `test oppdaterJournalpostMedMottaker`() {
        val jp = """
            {"journalpostId":"453976326","bruker":{"id":"2964001528817","type":"AKTOERID"},"tittel":"Utgående P6000 - Melding om vedtak","journalposttype":"U","journalstatus":"EKSPEDERT","tema":"PEN","behandlingstema":"ab0254","journalforendeEnhet":"4862","eksternReferanseId":"e05590fc-b93f-48a2-b7c9-2f838692fc68","tilleggsopplysninger":[{"nokkel":"eessi_pensjon_bucid","verdi":"1447240"}],"datoOpprettet":"2025-03-20T10:22:39"}
        """.trimIndent()
        val org = Organisation(
            name = "NAV Aremark",
            id = "123456789",
            address = null,
            countryCode = "NO",
            activeSince = null,
            acronym = null
        )
        every { safClient.hentJournalpostForJp(any())} returns mapJsonToAny<Journalpost>(jp)
        every { euxService.hentDeltakereForBuc(any())} returns org
        justRun { journalpostKlient.oppdaterJournalpostMedMottaker(any(), any()) }

        val oppdaterJPMedMottaker = OppdaterJPMedMottaker(safClient, euxService, journalpostKlient)
        oppdaterJPMedMottaker.oppdatereHeleSulamitten()

    }
}