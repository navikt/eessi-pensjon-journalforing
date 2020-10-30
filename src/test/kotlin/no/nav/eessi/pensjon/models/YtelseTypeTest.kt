package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class YtelseTypeTest {

    @Test
    fun `Verifiser serde fungerer som forventet`() {
        Assertions.assertEquals(YtelseType.OMSORG, serde(YtelseType.OMSORG))
        Assertions.assertEquals(YtelseType.ALDER, serde(YtelseType.ALDER))
        Assertions.assertEquals(YtelseType.GJENLEV, serde(YtelseType.GJENLEV))
        Assertions.assertEquals(YtelseType.BARNEP, serde(YtelseType.BARNEP))
        Assertions.assertEquals(YtelseType.UFOREP, serde(YtelseType.UFOREP))
        Assertions.assertEquals(YtelseType.GENRL, serde(YtelseType.GENRL))
    }

    private fun serde(ytelseType: YtelseType): YtelseType {
        val json = ytelseType.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}