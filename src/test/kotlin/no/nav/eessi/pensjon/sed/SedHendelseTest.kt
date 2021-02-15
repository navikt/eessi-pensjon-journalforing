package no.nav.eessi.pensjon.sed

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.eux.model.sed.SedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedHendelseTest {

    @Test
    fun `Gitt en gyldig SEDSendt json når mapping så skal alle felter mappes`() {
        val sedSendtJson = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText()
        val sedHendelse = SedHendelseModel.fromJson(sedSendtJson)
        assertEquals(sedHendelse.id, 1869L)
        assertEquals(sedHendelse.sedId, "P2000_b12e06dda2c7474b9998c7139c841646_2")
        assertEquals(sedHendelse.sektorKode, "P")
        assertEquals(sedHendelse.bucType, BucType.P_BUC_01)
        assertEquals(sedHendelse.rinaSakId, "147729")
        assertEquals(sedHendelse.avsenderId, "NO:NAVT003")
        assertEquals(sedHendelse.avsenderNavn, "NAVT003")
        assertEquals(sedHendelse.mottakerNavn, "NAV Test 07")
        assertEquals(sedHendelse.rinaDokumentId, "b12e06dda2c7474b9998c7139c841646")
        assertEquals(sedHendelse.rinaDokumentVersjon, "2")
        assertEquals(sedHendelse.sedType, SedType.P2000)
        assertEquals("09035225916", sedHendelse.navBruker!!.value)
    }
}
