package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KravSerdeTest {

    @Test
    fun `Serialize KravType`() {
        assertEquals("01", KravType.ALDER.verdi)
        assertEquals("02", KravType.GJENLEV.verdi)
        assertEquals("03", KravType.UFOREP.verdi)
    }

    @Test
    fun `Serde Krav object`() {
        val alder = Krav(dato = "01-01-2020", type = KravType.ALDER)
        assertEquals(alder, serde(alder))

        val gjenlev = Krav(dato = "01-01-2020", type = KravType.GJENLEV)
        assertEquals(gjenlev, serde(gjenlev))

        val ufore = Krav(dato = "01-01-2020", type = KravType.UFOREP)
        assertEquals(ufore, serde(ufore))
    }

    private fun serde(krav: Krav): Krav {
        val json = krav.toJson()

        return mapJsonToAny(json)
    }
}
