package no.nav.eessi.pensjon.personidentifisering

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class FnrHelperTest {

    private lateinit var helper: FnrHelper

    @BeforeEach
    fun setup() {
        helper = FnrHelper()
    }

    @Test
    fun `filtrer norsk pin annenperson med rolle 01`()   {
        val mapper = jacksonObjectMapper()
        val p2000json = getTestJsonFile("P2000-NAV.json")
        assertEquals(null, helper.filterAnnenpersonPinNode(mapper.readTree(p2000json)))

        val p10000json = getTestJsonFile("P10000-01Gjenlevende-NAV.json")
        val expected = "287654321"
        val actual  = helper.filterAnnenpersonPinNode(mapper.readTree(p10000json))
        assertEquals(expected, actual)

    }

    @Test
    fun `letter igjennom beste Sed på valgt buc P2100 også P2000 etter norsk personnr`() {
        val fnr = helper.getFodselsnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json")))
        val expected = "970970970"
        assertEquals(expected, fnr)
    }

    @Test
    fun `letter igjennom beste Sed på valgt buc etter norsk personnr`() {
        val actual = helper.getFodselsnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))
        val expected = "970970970"
        assertEquals(expected, actual)
    }

    @Test
    fun `letter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val actual = helper.getFodselsnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))
        val expected = "21712"
        assertEquals(expected, actual)
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val actual = helper.getFodselsnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000Gjennlevende-NAV.json")
        ))
        val expected = "21712"
        assertEquals(expected, actual)
    }


    private fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        Assertions.assertTrue(validateJson(json))
        return json
    }
}