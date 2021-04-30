package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        val alder = Krav(dato = "01-01-2020", type = KravType.ALDER)
        assertEquals(alder, serde(alder))

        val gjenlev = Krav(dato = "01-01-2020", type = KravType.ETTERLATTE)
        assertEquals(gjenlev, serde(gjenlev))

        val ufore = Krav(dato = "01-01-2020", type = KravType.UFORE)
        assertEquals(ufore, serde(ufore))
    }

    @Test
    fun `Deserialize krav`() {
        val alderJson = """{"dato":"01-01-2020","type":"01"}"""
        val alderKrav = mapJsonToAny(alderJson, typeRefs<Krav>())
        assertEquals(KravType.ALDER, alderKrav.type)

        val gjenlevJson = """{"dato":"01-01-2020","type":"02"}"""
        val gjenlevKrav = mapJsonToAny(gjenlevJson, typeRefs<Krav>())
        assertEquals(KravType.ETTERLATTE, gjenlevKrav.type)

        val uforeJson = """{"dato":"01-01-2020","type":"03"}"""
        val uforeKrav = mapJsonToAny(uforeJson, typeRefs<Krav>())
        assertEquals(KravType.UFORE, uforeKrav.type)

        val ugyldigJson = """{"dato":"01-01-2020","type":"5"}"""
        val ugyldigKrav = mapJsonToAny(ugyldigJson, typeRefs<Krav>())
        assertNull(ugyldigKrav.type)
    }

    private fun serde(krav: Krav): Krav {
        val json = krav.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}
