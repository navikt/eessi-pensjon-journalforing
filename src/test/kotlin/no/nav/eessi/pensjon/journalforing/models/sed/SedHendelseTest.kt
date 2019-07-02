package no.nav.eessi.pensjon.journalforing.models.sed

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

class SedHendelseTest {


    @Test
    fun `Gitt en gyldig SEDSendt json når mapping så skal alle felter mappes`() {

        val mapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01.json")))
        val sedHendelse = mapper.readValue(sedSendtJson, SedHendelseModel::class.java)
        assertEquals(sedHendelse.id, 1869)
        assertEquals(sedHendelse.sedId, "P2000_b12e06dda2c7474b9998c7139c841646_2")
        assertEquals(sedHendelse.sektorKode, "P")
        assertEquals(sedHendelse.bucType, SedHendelseModel.BucType.P_BUC_01)
        assertEquals(sedHendelse.rinaSakId, "147729")
        assertEquals(sedHendelse.avsenderId, "NO:NAVT003")
        assertEquals(sedHendelse.avsenderNavn, "NAVT003")
        assertEquals(sedHendelse.mottakerNavn, "NAV Test 07")
        assertEquals(sedHendelse.rinaDokumentId, "b12e06dda2c7474b9998c7139c841646")
        assertEquals(sedHendelse.rinaDokumentVersjon, "2")
        assertEquals(sedHendelse.sedType, SedHendelseModel.SedType.P2000)
        assertEquals(sedHendelse.navBruker, "12378945601")
    }
}