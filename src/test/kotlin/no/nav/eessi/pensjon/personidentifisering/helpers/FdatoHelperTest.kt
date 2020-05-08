package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.json.validateJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

class FdatoHelperTest {

    private lateinit var helper: FdatoHelper

    @BeforeEach
    fun setup() {
        helper = FdatoHelper()
    }

    @Test
    fun `Calling getFDatoFromSed returns exception when foedselsdato is not found` () {
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            helper.finnEnFdatoFraSEDer(listOf(getTestJsonFile("EmptySED.json")))
        }
    }

    @Test
    fun `Calling getFDatoFromSed returns valid fdato when found in first valid SED` () {
        val actual = helper.finnEnFdatoFraSEDer(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))
        val expected = LocalDate.of(1969,9,11)
        assertEquals(expected, actual)
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_01` () {
        assertEquals(LocalDate.of(1980,1,1), helper.finnEnFdatoFraSEDer(listOf(getTestJsonFile("P2000-NAV.json"))))
    }

    @Test
    fun `ved henting ved fdato på R005 når kun en person hentes personen` () {
        assertEquals(LocalDate.of(1980,10,22), helper.finnEnFdatoFraSEDer(listOf(getSedTestJsonFile("R_BUC_02-R005-IkkePin.json"))))
    }

    @Test
    fun `ved henting ved fdato på R005 når det er flere personer og en er avød hentes den avdøde` () {
        assertEquals(LocalDate.of(2000,8,26), helper.finnEnFdatoFraSEDer(listOf(getSedTestJsonFile("R005-avdod-enke-NAV.json"))))
    }

    @Test
    fun `ved henting ved fdato på R005 når det er flere personer og ingen er avdød feiler det` () {
        assertThrows<RuntimeException> {
            helper.finnEnFdatoFraSEDer(listOf(getSedTestJsonFile("R005-enke-ikkeavdod-NAV.json")))
        }
    }

    private fun getSedTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/sed/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }

    private fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }
}
