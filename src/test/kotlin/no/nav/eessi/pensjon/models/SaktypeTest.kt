package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class SaktypeTest {

    @ParameterizedTest
    @EnumSource(SakType::class)
    fun `Verifiser serde fungerer som forventet`(type: SakType) {
        Assertions.assertEquals(type, serde(type))
    }

    private fun serde(saktype: SakType): SakType {
        val json = saktype.toJson()

        return mapJsonToAny(json)
    }
}