package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RolleSerdeTest {

    @Test
    fun `Serialize KravType`() {
        assertEquals("\"01\"", Rolle.ETTERLATTE.toJson())
        assertEquals("\"02\"", Rolle.FORSORGER.toJson())
        assertEquals("\"03\"", Rolle.BARN.toJson())
    }

    @Test
    fun `Serde Krav object`() {
        val etterlatte = Rolle.ETTERLATTE
        assertEquals(Rolle.ETTERLATTE, serde(etterlatte))

        val forsorger = Rolle.FORSORGER
        assertEquals(Rolle.FORSORGER, serde(forsorger))

        val barn = Rolle.BARN
        assertEquals(Rolle.BARN, serde(barn))
    }

    private fun serde(rolle: Rolle): Rolle {
        val json = rolle.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}