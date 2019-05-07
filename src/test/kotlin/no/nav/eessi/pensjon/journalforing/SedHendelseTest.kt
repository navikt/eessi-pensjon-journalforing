package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SedHendelseTest {


    @Test
    fun `Gitt en gyldig SEDSendt json når mapping så skal alle felter mappes`() {

        val mapper = jacksonObjectMapper()
        val sedSendtJson = String(Files.readAllBytes(Paths.get("src/test/resources/sedsendt/P_BUC_01.json")))
        val sedHendelse = mapper.readValue(sedSendtJson, SedHendelse::class.java)
        assertEquals(sedHendelse.id, 1869)
        assertEquals(sedHendelse.sedId, "P2000_b12e06dda2c7474b9998c7139c841646_2")
        assertEquals(sedHendelse.sektorKode, "P")
        assertEquals(sedHendelse.bucType, "P_BUC_01")
        assertEquals(sedHendelse.rinaSakId, "147729")
        assertEquals(sedHendelse.avsenderId, "NO:NAVT003")
        assertEquals(sedHendelse.avsenderNavn, "NAVT003")
        assertEquals(sedHendelse.mottakerNavn, "NAV Test 07")
        assertEquals(sedHendelse.rinaDokumentId, "b12e06dda2c7474b9998c7139c841646")
        assertEquals(sedHendelse.rinaDokumentVersjon, "2")
        assertEquals(sedHendelse.sedType, "P2000")
        assertEquals(sedHendelse.navBruker, "12378945601")
    }
}