package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
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
        assertTrue(actual.contains(PersonRelasjon(expectedFnr,Relasjon.FORSIKRET, sedType = SedType.P2000)))
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json")))

        val expected = setOf(PersonRelasjon(fnr="67097097000", relasjon= Relasjon.FORSIKRET, sedType = SedType.P2000))

        assertEquals(1,actual.size)
        assertTrue(actual.containsAll(expected))
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))

        val expectedFnr = "21712000000"
        assertEquals(1,actual.size)
        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET, YtelseType.ALDER, SedType.P15000), actual.first())
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000Gjennlevende-NAV.json")
        ))
        val expectedFnr = "97097097000"
        assertEquals(1,actual.size)
        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET, YtelseType.GJENLEV, SedType.P15000), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere personer etter fnr på avdød`() {

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getSedTestJsonFile("R005-avdod-enke-NAV.json")))

        val enke = PersonRelasjon("28125518943", Relasjon.GJENLEVENDE, sedType = SedType.R005)
        val avdod = PersonRelasjon("28115518943", Relasjon.AVDOD, sedType = SedType.R005)

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
        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET, sedType = SedType.R005), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person uten pin`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R_BUC_02-R005-IkkePin.json")))
        assert(actual.isEmpty())
    }

    @Test
    fun `Gitt en R_BUC og sed R005 med flere flere personer så returner det en liste med Relasjon`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R005-personer-debitor-alderpensjon-NAV.json")))
        val forste = PersonRelasjon("02087922262", Relasjon.ANNET, sedType = SedType.R005)
        val andre = PersonRelasjon("04117922400", Relasjon.ANNET, sedType = SedType.R005)

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
        val forste = PersonRelasjon("04117922400", Relasjon.ANNET, sedType = SedType.R005)

        assertEquals(1,actual.size)
        assertTrue(actual.contains(forste))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere person ikke avdød`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R005-enke-ikkeavdod-NAV.json")))
        val enke = PersonRelasjon("28125518943", Relasjon.GJENLEVENDE, sedType = SedType.R005)
        val annen = PersonRelasjon("28115518943", Relasjon.ANNET, sedType = SedType.R005)

        assertEquals(2,actual.size)
        assertTrue(actual.contains(enke))
        assertTrue(actual.contains(annen))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person debitor alderpensjon returnerer liste med en Relasjon`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(getSedTestJsonFile("R005-alderpensjon-NAV.json")))
        val annen = PersonRelasjon("04117922400", Relasjon.ANNET, sedType = SedType.R005)

        assertEquals(1,actual.size)
        assertTrue(actual.contains(annen))
    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres kun en gjenlevende uten ytelsestype`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getSedTestJsonFile("P5000-medNorskGjenlevende-NAV.json"),
                getSedTestJsonFile("P2100-utenNorskGjenlevende-NAV.json")))

        val expectedPersonRelasjon = PersonRelasjon("12312312312", Relasjon.GJENLEVENDE, null, sedType = SedType.P5000)

        assertEquals(1,actual.size)

        val actualPersonRelasjon = actual.first()
        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)

    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres minst en gjenlevende og en avdød`() {
        val gjenlevFnr = "48035849680"

        val mockP2100 = mockP2100()
        val mockP5000 = mockP5000("25105424704", gjenlevFnr)
        val mockP8000 = mockP8000()

        val actual = helper.getPotensielleFnrFraSeder(listOf(mockP2100, mockP5000, mockP8000))

        val expectedPersonRelasjon = PersonRelasjon(gjenlevFnr, Relasjon.GJENLEVENDE, YtelseType.GJENLEV, SedType.P5000)

        assertEquals(1, actual.size)

        val actualPersonRelasjon = actual.first()
        assertEquals(gjenlevFnr, actualPersonRelasjon.fnr)
        assertEquals(Relasjon.GJENLEVENDE, actualPersonRelasjon.relasjon)

        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
    }

    private fun mockP8000(): String {
        return """
            {
              "sed" : "P8000",
              "sedGVer" : "4",
              "sedVer" : "1",
              "nav" : {
                "eessisak" : [ {
                  "institusjonsid" : "NO:noinst002",
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "saksnummer" : "22874955",
                  "land" : "NO"
                } ],
                "bruker" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "25105424704",
                      "land" : "NO"
                    } ],
                    "statsborgerskap" : [ {
                      "land" : "XQ"
                    } ],
                    "etternavn" : "KAFFI",
                    "fornavn" : "ÅPENHJERTIG",
                    "kjoenn" : "M",
                    "foedselsdato" : "1954-10-25"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "postnummer" : "1920",
                    "land" : "DE"
                  }
                },
                "krav" : {
                  "dato" : "2020-09-17"
                },
                "annenperson" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "48035849680",
                      "land" : "NO"
                    } ],
                    "statsborgerskap" : [ {
                      "land" : "DE"
                    } ],
                    "etternavn" : "KONSOLL",
                    "fornavn" : "LEALAUS",
                    "kjoenn" : "K",
                    "foedselsdato" : "1958-03-08",
                    "rolle" : "01"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "postnummer" : "1920",
                    "land" : "XQ"
                  }
                }
              },
              "pensjon" : null
            }
        """.trimIndent()
    }

    private fun mockP2100(): String {
        return """
            {
              "pensjon": {
                "gjenlevende": {
                  "person": {
                    "kjoenn": "K",
                    "foedselsdato": "1958-03-08",
                    "relasjontilavdod": {
                      "relasjon": "03"
                    },
                    "etternavn": "KONSOLL",
                    "fornavn": "LEALAUS"
                  }
                }
              },
              "nav": {
                "bruker": {
                  "person": {
                    "kjoenn": "M",
                    "dodsDetalj": {
                      "dato": "2020-08-15"
                    },
                    "etternavn": "KAFFI",
                    "fornavn": "ÅPENHJERTIG",
                    "foedselsdato": "1954-10-25"
                  }
                },
                "eessisak": [
                  {
                    "institusjonsid": "NO:NAVAT08",
                    "institusjonsnavn": "NAV ACCEPTANCE TEST 08",
                    "saksnummer": "123456",
                    "land": "SE"
                  }
                ],
                "krav": {
                  "dato": "2020-08-18"
                }
              },
              "sedGVer": "4",
              "sedVer": "2",
              "sed": "P2100"
            }
        """.trimIndent()
    }

    private fun mockP5000(forsikretFnr: String?, gjenlevFnr: String?): String {
        return """
            {
              "sedGVer": "4",
              "nav": {
                "bruker": {
                  "person": {
                    "fornavn": "ÅPENHJERTIG",
                    "pin": [
                      {
                        "land": "NO",
                        "institusjonsid": "NO:NAVAT07",
                        "institusjonsnavn": "NAV ACCEPTANCE TEST 07",
                        "identifikator": "$forsikretFnr"
                      }
                    ],
                    "kjoenn": "M",
                    "etternavn": "KAFFI",
                    "foedselsdato": "1954-10-25",
                    "statsborgerskap": [
                      {
                        "land": "DE"
                      }
                    ]
                  }
                },
                "eessisak": [
                  {
                    "institusjonsnavn": "NAV ACCEPTANCE TEST 07",
                    "saksnummer": "22916371",
                    "institusjonsid": "NO:NAVAT07",
                    "land": "NO"
                  }
                ]
              },
              "sedVer": "2",
              "sed": "P5000"
              ${if (gjenlevFnr != null) createGjenlevende(gjenlevFnr, "02") else ""}
            }
        """.trimIndent()
    }

    private fun mockP15000(fnr: String?, gjenlevFnr: String? = null, eessiSaknr: String? = null, krav: String, relasjon: String? = null): String {
        return """
            {
              "sed" : "P15000",
              "nav" : {
                ${if (eessiSaknr != null) createEESSIsakJson(eessiSaknr) else ""}
                "bruker" : {
                  "person" : {
                    "statsborgerskap" : [ {
                      "land" : "NO"
                    } ],
                    "etternavn" : "Forsikret",
                    "fornavn" : "Person",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12"
                    ${if (fnr != null) createPinJson(fnr) else ""}
                  }
                  },
                  "krav" : {
                    "dato" : "2019-02-01",
                    "type" : "$krav"
                }
              }
            ${if (gjenlevFnr != null) createGjenlevende(gjenlevFnr, relasjon) else ""}
            }
        """.trimIndent()
    }

    private fun createPinJson(fnr: String?): String {
        return """
             ,"pin": [
                      {
                        "land": "NO",
                        "identifikator": "$fnr"
                      }
                    ]
        """.trimIndent()
    }

    private fun createEESSIsakJson(saknr: String?): String {
        return """
            "eessisak": [
              {
                "saksnummer": "$saknr",
                "land": "NO"
              }
            ],            
        """.trimIndent()
    }

    private fun createGjenlevende(fnr: String?, relasjon: String? = null): String {
        return """
          ,
          "pensjon" : {
            "gjenlevende" : {
              "person" : {
                "statsborgerskap" : [ {
                  "land" : "DE"
                } ],
                "etternavn" : "Gjenlev",
                "fornavn" : "Lever",
                "kjoenn" : "M",
                "foedselsdato" : "1988-07-12",
                "relasjontilavdod" : {
                ${if (relasjon != null) "\"relasjon\" : \"$relasjon\"" else ""}
                }
                ${if (fnr != null) createPinJson(fnr) else ""}
              }
            }
          }
        """.trimIndent()
    }

    @Test
    fun `To personer, to SED P5000 og P15000, med GJENLEV, skal hente gyldige relasjoner fra P15000`() {
        val forsikretFnr = "97097097000"
        val gjenlevFnr = "48035849680"

        val mockP15000 = mockP15000(
                fnr = forsikretFnr,
                gjenlevFnr = gjenlevFnr,
                krav = "02",
                eessiSaknr = "12345",
                relasjon = "01"
        )
        val mockP5000 = mockP5000(forsikretFnr, gjenlevFnr)

        val actual = helper.getPotensielleFnrFraSeder(listOf(mockP15000, mockP5000))

        val expectedForsikret = PersonRelasjon(forsikretFnr, Relasjon.FORSIKRET, YtelseType.GJENLEV, sedType = SedType.P15000)
        val expectedGjenlev = PersonRelasjon(gjenlevFnr, Relasjon.GJENLEVENDE, YtelseType.GJENLEV, sedType = SedType.P15000)

        assertEquals(2, actual.size)

        assertEquals(expectedGjenlev, actual.last())
        assertEquals(expectedForsikret, actual.first())
    }

    @Test
    fun `En person, to SED P5000 og P15000, med ALDER, skal hente gyldige relasjoner fra P15000`() {
        val forsikretFnr = "97097097000"

        val mockP15000 = mockP15000(
                fnr = forsikretFnr,
                krav = "01",
                eessiSaknr = "12345"
        )
        val mockP5000 = mockP5000(forsikretFnr, gjenlevFnr = null)

        val actual = helper.getPotensielleFnrFraSeder(listOf(mockP15000, mockP5000))

        val expectedPerson = PersonRelasjon(forsikretFnr, Relasjon.FORSIKRET, YtelseType.ALDER, sedType = SedType.P15000)

        assertEquals(1, actual.size)
        assertEquals(expectedPerson, actual.first())
    }

    @Test
    fun `Gitt at vi har en P2100 med gjenlevende og avdød så skal det returneres to personrelasjoner`() {
        val mockP2100 = """
            {
              "pensjon": {
                "gjenlevende": {
                  "person": {
                    "pin": [
                      {
                        "identifikator": "430-035 849 68",
                        "institusjonsnavn": "NAV ACCEPTANCE TEST 07",
                        "land": "NO",
                        "institusjonsid": "NO:NAVAT07"
                      }
                    ],
                    "foedselsdato": "1958-03-08",
                    "relasjontilavdod": {
                      "relasjon": "03"
                    },
                    "etternavn": "KONSOLL",
                    "fornavn": "LEALAUS",
                    "kjoenn": "K",
                    "statsborgerskap": [
                      {
                        "land": "DE"
                      }
                    ]
                  }
                }
              },
              "sedGVer": "4",
              "nav": {
                "bruker": {
                  "person": {
                    "fornavn": "ÅPENHJERTIG",
                    "pin": [
                      {
                        "land": "NO",
                        "institusjonsid": "NO:NAVAT07",
                        "institusjonsnavn": "NAV ACCEPTANCE TEST 07",
                        "identifikator": "25/10-5424 704"
                      }
                    ],
                    "kjoenn": "M",
                    "etternavn": "KAFFI",
                    "foedselsdato": "1954-10-25",
                    "statsborgerskap": [
                      {
                        "land": "DE"
                      }
                    ]
                  }
                },
                "eessisak": [
                  {
                    "institusjonsnavn": "NAV ACCEPTANCE TEST 07",
                    "saksnummer": "22916371",
                    "institusjonsid": "NO:NAVAT07",
                    "land": "NO"
                  }
                ]
              },
              "sedVer": "2",
              "sed": "P2100"
            }
        """.trimIndent()

        val actual = helper.getPotensielleFnrFraSeder(listOf(mockP2100))

        val expectedPersonRelasjon = PersonRelasjon("43003584968", Relasjon.GJENLEVENDE, YtelseType.GJENLEV, sedType = SedType.P2100)

        assertEquals(1, actual.size)

        val personRelasjon = actual.first()
        assertEquals("43003584968", personRelasjon.fnr)
        assertEquals(Relasjon.GJENLEVENDE, personRelasjon.relasjon)

        assertEquals(expectedPersonRelasjon, personRelasjon)

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