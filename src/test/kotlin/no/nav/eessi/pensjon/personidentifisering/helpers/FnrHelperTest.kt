package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    fun `leter igjennom beste Sed på valgt buc P2100 også P2000 etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json")))

        val expectedFnr = "67097097000"
        assertEquals(1,actual.size)
        assertTrue(actual.contains(PersonRelasjon(expectedFnr,Relasjon.FORSIKRET)))
        val nav = NavFodselsnummer(expectedFnr)
        assertEquals(nav.isDNumber(), true)
        assertEquals(nav.getBirthDateAsISO(), "1970-09-27")
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))

        val expected = setOf(PersonRelasjon(fnr="67097097000", relasjon= Relasjon.FORSIKRET),
                PersonRelasjon(fnr="21712000000", relasjon= Relasjon.FORSIKRET))

        assertEquals(2,actual.size)
        assertTrue(actual.containsAll(expected))
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))

        val expectedFnr = "21712000000"
        assertEquals(1,actual.size)
        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET), actual.first())
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000Gjennlevende-NAV.json")
        ))
        val expectedFnr = "97097097000"
        assertEquals(1,actual.size)
        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere personer etter fnr på avdød`() {

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getSedTestJsonFile("R005-avdod-enke-NAV.json")))

        val enke = PersonRelasjon("28125518943", Relasjon.GJENLEVENDE)
        val avdod = PersonRelasjon("28115518943", Relasjon.AVDOD)

        assertEquals(2,actual.size)
        assertTrue(actual.contains(enke))
        assertTrue(actual.contains(avdod))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person returnerer fnr`() {
        val expectedFnr = "28115518943"
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getSedTestJsonFile("R_BUC_02-R005-AP.json")))
        assertEquals(1,actual.size)
        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person uten pin`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R_BUC_02-R005-IkkePin.json")))
        assert(actual.isEmpty())
    }

    @Test
    fun `Gitt en R_BUC og sed R005 med flere flere personer så returner det en liste med Relasjon`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R005-personer-debitor-alderpensjon-NAV.json")))
        val forste = PersonRelasjon("02087922262", Relasjon.ANNET)
        val andre = PersonRelasjon("04117922400", Relasjon.ANNET)

        assertEquals(2,actual.size)
        assertTrue(actual.contains(forste))
        assertTrue(actual.contains(andre))
    }

    @Test
    fun `Gitt en R_BUC og flere seder har samme person så returnerer vi en unik liste med en Relasjon`() {
        val actual = helper.getPotensielleFnrFraSeder(
                listOf(
                        getSedTestJsonFile("R005-alderpensjon-NAV.json"),
                        getSedTestJsonFile("R_BUC_02_H070-NAV.json")
                ))
        val forste = PersonRelasjon("04117922400", Relasjon.ANNET)

        assertEquals(1,actual.size)
        assertTrue(actual.contains(forste))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere person ikke avdød`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R005-enke-ikkeavdod-NAV.json")))
        val enke = PersonRelasjon("28125518943", Relasjon.GJENLEVENDE)
        val annen = PersonRelasjon("28115518943", Relasjon.ANNET)

        assertEquals(2,actual.size)
        assertTrue(actual.contains(enke))
        assertTrue(actual.contains(annen))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person debitor alderpensjon returnerer liste med en Relasjon`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R005-alderpensjon-NAV.json")))
        val annen = PersonRelasjon("04117922400", Relasjon.ANNET)

        assertEquals(1,actual.size)
        assertTrue(actual.contains(annen))
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