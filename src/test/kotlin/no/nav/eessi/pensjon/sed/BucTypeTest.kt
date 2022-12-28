package no.nav.eessi.pensjon.sed

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class BucTypeTest {

    @ParameterizedTest
    @EnumSource(BucType::class)
    fun `Verifiser serde av BucType fungerer`(type: BucType) {
        assertEquals(type, serde(type))
    }

    private fun serde(bucType: BucType): BucType {
        val json = bucType.toJson()

        return mapJsonToAny(json)
    }
}