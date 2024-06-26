package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GyldigeHendelserTest {

    @Test
    fun `Mottatt hendelse R_BUC_02 er gyldig`() {
        val hendelse = createDummy("", R_BUC_02)

        assertTrue(GyldigeHendelser.mottatt(hendelse))
    }

    @Test
    fun `Mottatt hendelse H_BUC_07 er gyldig`() {
        val hendelse = createDummy("X", H_BUC_07)

        assertTrue(GyldigeHendelser.mottatt(hendelse))
    }

    @Test
    fun `Mottatt hendelse som IKKE er R_BUC_02, H_BUC_07, eller sektorkode P er ugyldig`() {
        val hendelse = createDummy("", P_BUC_01)

        assertFalse(GyldigeHendelser.mottatt(hendelse))
    }

    @Test
    fun `Mottatt hendelse som mangler BucType`() {
        val hendelse = createDummy("", null)

        assertFalse(GyldigeHendelser.mottatt(hendelse))
    }


    /**
     * SENDTE HENDELSER
     */
    @Test
    fun `Sendt hendelse R_BUC_02 er gyldig`() {
        val hendelse = createDummy("X", R_BUC_02)

        assertTrue(GyldigeHendelser.sendt(hendelse))
    }

    @Test
    fun `Sendt hendelse H_BUC_07 er ikke gyldig`() {
        val hendelse = createDummy("H", H_BUC_07)

        assertFalse(GyldigeHendelser.sendt(hendelse))
    }

    @Test
    fun `Sendt hendelse som mangler BucType`() {
        val hendelse = createDummy("", null)

        assertFalse(GyldigeHendelser.mottatt(hendelse))
    }

    private fun createDummy(sektor: String, bucType: BucType?) =
        SedHendelse(sektorKode = sektor, bucType = bucType, rinaSakId = "12345", rinaDokumentId = "654634", rinaDokumentVersjon = "1")
}
