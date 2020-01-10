package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.personidentifisering.services.FdatoHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class FdatoHelperTest {

    private lateinit var helper: FdatoHelper

    @BeforeEach
    fun setup() {
        helper = FdatoHelper()
    }

    @Test
    fun `Calling getFDatoFromSed returns exception when foedselsdato is not found` () {
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            helper.finnFDatoFraSeder(listOf(getTestJsonFile("EmptySED.json")))
        }
    }

    @Test
    fun `Calling getFDatoFromSed returns valid fdato when found in first valid SED` () {
        val actual = helper.finnFDatoFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))
        val expected = "1969-09-11"
        assertEquals(expected, actual)
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_01` () {
        assertEquals("1980-01-01", helper.finnFDatoFraSeder(listOf(getTestJsonFile("P2000-NAV.json"))))
    }

    private fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }
}