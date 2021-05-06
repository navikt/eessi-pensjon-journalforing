package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KravSerdeTest {

    @Test
    fun `Serialize KravType`() {
        assertEquals("\"01\"", KravType.ALDER.toJson())
        assertEquals("\"02\"", KravType.ETTERLATTE.toJson())
        assertEquals("\"03\"", KravType.UFORE.toJson())
    }

    @Test
    fun `Serde Krav object`() {
        val alder = Krav(dato = "01-01-2020", type = KravType.ALDER.kode)
        assertEquals(alder, serde(alder))

        val gjenlev = Krav(dato = "01-01-2020", type = KravType.ETTERLATTE.kode)
        assertEquals(gjenlev, serde(gjenlev))

        val ufore = Krav(dato = "01-01-2020", type = KravType.UFORE.kode)
        assertEquals(ufore, serde(ufore))
    }

    private fun serde(krav: Krav): Krav {
        val json = krav.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}
