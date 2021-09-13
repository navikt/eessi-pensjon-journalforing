package no.nav.eessi.pensjon.sed

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

internal class SedHendelseModelSerdeTest {

    @Test
    fun `Sjekk at serialisering virker`() {
        val model = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType = BucType.R_BUC_02, rinaDokumentVersjon = "1")
        val serialized = model.toJson()

        val result = SedHendelseModel.fromJson(serialized)

        assertEquals(model, result)
    }

    @Test
    fun `Sjekker at deserialisering gir riktig verdi`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "P_BUC_02",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null,
            "navBruker" : null
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        val result = model.toJson()
        JSONAssert.assertEquals(json, result, JSONCompareMode.LENIENT)
    }

    @Test
    fun `Deserialisering med ugyldig bucType skal gi bucType null`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "FB_BUC_01",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null,
            "navBruker" : null
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        assertEquals("R", model.sektorKode)
        assertNull(model.bucType)
    }

    @Test
    fun `Deserialisering med gyldig fnr`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "FB_BUC_01",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null,
            "navBruker" : "22117320034"
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        assertEquals("22117320034", model.navBruker!!.value)
    }

    @Test
    fun `Deserialisering med ugyldig fnr`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "FB_BUC_01",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null,
            "navBruker" : "1234"
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        assertNull(model.navBruker)
    }
}
