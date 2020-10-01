package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.validateJson
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

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres kun en gjenlevende uten ytelsestype`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                getSedTestJsonFile("P5000-medNorskGjenlevende-NAV.json"),
                getSedTestJsonFile("P2100-utenNorskGjenlevende-NAV.json")))

        val expectedPersonRelasjon = PersonRelasjon("12312312312", Relasjon.GJENLEVENDE, null)

        assertEquals(1,actual.size)

        val actualPersonRelasjon = actual.first()
        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)

    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres minst en gjenlevende og en avdød`() {

        val mockP2100 = """
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

        val mockP5000 = """
            {
              "pensjon": {
                "medlemskapTotal": [
                  {
                    "type": "10",
                    "sum": {
                      "aar": "7",
                      "dager": {
                        "type": "7"
                      }
                    },
                    "relevans": "111"
                  }
                ],
                "gjenlevende": {
                  "person": {
                    "pin": [
                      {
                        "identifikator": "48035849680",
                        "institusjonsnavn": "NAV ACCEPTANCE TEST 07",
                        "land": "NO",
                        "institusjonsid": "NO:NAVAT07"
                      }
                    ],
                    "foedselsdato": "1958-03-08",
                    "etternavn": "KONSOLL",
                    "fornavn": "LEALAUS",
                    "kjoenn": "K",
                    "statsborgerskap": [
                      {
                        "land": "DE"
                      }
                    ]
                  }
                },
                "medlemskap": [
                  {
                    "gyldigperiode": "1",
                    "enkeltkrav": {
                      "krav": "20"
                    },
                    "periode": {
                      "tom": "2016-12-31",
                      "fom": "2010-01-01"
                    }
                  }
                ],
                "trygdetid": [
                  {
                    "sum": {
                      "dager": {
                        "type": "7"
                      },
                      "aar": "7"
                    },
                    "type": "10"
                  }
                ]
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
                        "identifikator": "25105424704"
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
            }
        """.trimIndent()

        val mockP8000 = """
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

        val actual = helper.getPotensielleFnrFraSeder(listOf(mockP2100,mockP5000, mockP8000))

        val expectedAvdodPersonRelasjon = PersonRelasjon("25105424704", Relasjon.FORSIKRET, null)
        val expectedPersonRelasjon = PersonRelasjon("48035849680", Relasjon.GJENLEVENDE, null)

        assertEquals(2,actual.size)

        val actualAvdodPerson = actual.first()
        val actualPersonRelasjon = actual.last()
        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
        assertEquals(expectedAvdodPersonRelasjon, actualAvdodPerson)

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

        val expectedAvdodPersonRelasjon = PersonRelasjon("25105424704", Relasjon.FORSIKRET, null)
        val expectedPersonRelasjon = PersonRelasjon("43003584968", Relasjon.GJENLEVENDE, YtelseType.GJENLEV)

        assertEquals(2,actual.size)

        val actualAvdodPerson = actual.first()
        val actualPersonRelasjon = actual.last()
        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
        assertEquals(expectedAvdodPersonRelasjon, actualAvdodPerson)

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