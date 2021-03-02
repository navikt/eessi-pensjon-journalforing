package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class SaktypeTest {

    @ParameterizedTest
    @EnumSource(Saktype::class)
    fun `Verifiser serde fungerer som forventet`(type: Saktype) {
        Assertions.assertEquals(type, serde(type))
    }

    private fun serde(saktype: Saktype): Saktype {
        val json = saktype.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}