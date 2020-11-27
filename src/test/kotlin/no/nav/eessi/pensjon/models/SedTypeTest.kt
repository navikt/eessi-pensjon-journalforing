package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class SedTypeTest {
    @ParameterizedTest
    @EnumSource(SedType::class)
    fun `Verifiser serde av SedType fungerer`(type: SedType) {
        Assertions.assertEquals(type, serde(type))
    }

    private fun serde(sedType: SedType): SedType {
        val json = sedType.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}