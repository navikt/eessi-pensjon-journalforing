package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.sed.SedHendelseModel
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

        assertEquals(BucType.UKJENT,model.bucType)
    }
}