package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class YtelseTypeTest {

    @ParameterizedTest
    @EnumSource(YtelseType::class)
    fun `Verifiser serde fungerer som forventet`(type: YtelseType) {
        Assertions.assertEquals(type, serde(type))
    }

    private fun serde(ytelseType: YtelseType): YtelseType {
        val json = ytelseType.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}