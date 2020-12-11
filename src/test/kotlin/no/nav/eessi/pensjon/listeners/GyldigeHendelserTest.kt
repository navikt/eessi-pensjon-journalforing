package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GyldigeHendelserTest {

    private val gyldigeHendelser = GyldigeHendelser()

    @Test
    fun `Mottatt hendelse R_BUC_02 er gyldig`() {
        val hendelse = createDummy("", BucType.R_BUC_02)

        assertTrue(gyldigeHendelser.mottattHendelse(hendelse))
    }

    @Test
    fun `Mottatt hendelse H_BUC_07 er gyldig`() {
        val hendelse = createDummy("X", BucType.H_BUC_07)

        assertTrue(gyldigeHendelser.mottattHendelse(hendelse))
    }

    @Test
    fun `Mottatt hendelse som IKKE er R_BUC_02, H_BUC_07, eller sektorkode P er ugyldig`() {
        val hendelse = createDummy("", BucType.P_BUC_01)

        assertFalse(gyldigeHendelser.mottattHendelse(hendelse))
    }

    @Test
    fun `Mottatt hendelse som mangler BucType`() {
        val hendelse = createDummy("", null)

        assertFalse(gyldigeHendelser.mottattHendelse(hendelse))
    }


    /**
     * SENDTE HENDELSER
     */
    @Test
    fun `Sendt hendelse R_BUC_02 er gyldig`() {
        val hendelse = createDummy("X", BucType.R_BUC_02)

        assertTrue(gyldigeHendelser.sendtHendelse(hendelse))
    }

    @Test
    fun `Sendt hendelse H_BUC_07 er ikke gyldig`() {
        val hendelse = createDummy("H", BucType.H_BUC_07)

        assertFalse(gyldigeHendelser.sendtHendelse(hendelse))
    }

    @Test
    fun `Sendt hendelse som mangler BucType`() {
        val hendelse = createDummy("", null)

        assertFalse(gyldigeHendelser.mottattHendelse(hendelse))
    }

    private fun createDummy(sektor: String, bucType: BucType?) =
            SedHendelseModel(sektorKode = sektor, bucType = bucType, rinaSakId = "12345", rinaDokumentId = "654634")
}
