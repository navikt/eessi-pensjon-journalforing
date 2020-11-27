package no.nav.eessi.pensjon.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class BucTypeTest {

    @Test
    fun `Sjekker at UKJENT BucType ikke feiler`() {
        val json = """{
            "sektorKode" : "R",
            "bucType" : "NOE DUMT",
            "rinaSakId" : "123456",
            "rinaDokumentId" : "1234"
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)
        assertEquals(BucType.UKJENT, model.bucType)

        val ukjentBucType = mapJsonToAny("\"buc finnes ikke\"", typeRefs<BucType>())
        assertEquals(BucType.UKJENT, ukjentBucType)
    }

    @ParameterizedTest
    @EnumSource(BucType::class)
    fun `Verifiser serde av BucType fungerer`(type: BucType) {
        assertEquals(type, serde(type))
    }

    private fun serde(bucType: BucType): BucType {
        val json = bucType.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}