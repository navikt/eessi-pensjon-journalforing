package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JournalforingServiceSakIdTest : JournalforingServiceBase() {

    @Test
    fun `hentSak skal gi sak ved treff mot tidligere gjennySak`() {
        val euxCaseId = "123"
        val gjennySakJson = """{ "sakId": "sakId123", "sakType": "EY"}""".trimIndent()

        every { gcpStorageService.gjennyFinnes(euxCaseId) } returns true
        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns gjennySakJson

        val result = journalforingService.hentSak(euxCaseId)

        assertEquals(Sak("FAGSAK", "sakId123", "EY"), result)
        verify { gcpStorageService.gjennyFinnes(euxCaseId) }
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `hentSak skal gi Sak fra sakIdFraSed naar gjennySak mangler`() {
        val euxCaseId = "123"
        val sakIdFraSed = "sakIdSed"

        every { gcpStorageService.gjennyFinnes(euxCaseId) } returns false

        val result = journalforingService.hentSak(euxCaseId, sakIdFraSed)

        assertEquals(Sak("FAGSAK", sakIdFraSed, "EY"), result)
        verify { gcpStorageService.gjennyFinnes(euxCaseId) }
    }

    @Test
    fun `hentSak skal gi Sak fra sakInformasjon naar gjennySak og sakIdFraSed mangler`() {
        val euxCaseId = "123"
        val sakInformasjon = SakInformasjon("32131", SakType.GJENLEV, LOPENDE)

        every { gcpStorageService.gjennyFinnes(euxCaseId) } returns false

        val result = journalforingService.hentSak(euxCaseId, sakInformasjon = sakInformasjon)

        assertEquals(Sak("FAGSAK", sakInformasjon.sakId!!, "EY"), result)
        verify { gcpStorageService.gjennyFinnes(euxCaseId) }
    }

    @Test
    fun `hentSak skal gi null when naar alt mangler `() {
        val euxCaseId = "123"

        every { gcpStorageService.gjennyFinnes(euxCaseId) } returns false

        val result = journalforingService.hentSak(euxCaseId)

        assertNull(result)
        verify { gcpStorageService.gjennyFinnes(euxCaseId) }
    }
}