package no.nav.eessi.pensjon.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

    @Test
    fun `Verifiser serde av BucType fungerer`() {
        assertEquals(BucType.P_BUC_01, serde(BucType.P_BUC_01))
        assertEquals(BucType.P_BUC_02, serde(BucType.P_BUC_02))
        assertEquals(BucType.P_BUC_03, serde(BucType.P_BUC_03))
        assertEquals(BucType.P_BUC_04, serde(BucType.P_BUC_04))
        assertEquals(BucType.P_BUC_05, serde(BucType.P_BUC_05))
        assertEquals(BucType.P_BUC_06, serde(BucType.P_BUC_06))
        assertEquals(BucType.P_BUC_07, serde(BucType.P_BUC_07))
        assertEquals(BucType.P_BUC_08, serde(BucType.P_BUC_08))
        assertEquals(BucType.P_BUC_09, serde(BucType.P_BUC_09))
        assertEquals(BucType.P_BUC_10, serde(BucType.P_BUC_10))
        assertEquals(BucType.H_BUC_07, serde(BucType.H_BUC_07))
        assertEquals(BucType.R_BUC_02, serde(BucType.R_BUC_02))
    }

    private fun serde(bucType: BucType): BucType {
        val json = bucType.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}