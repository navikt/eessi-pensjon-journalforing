package no.nav.eessi.pensjon.buc

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.PensjonSak
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths


@ExtendWith(MockitoExtension::class)
class SedDokumentHelperTest {

    @Mock
    private lateinit var euxKlient: EuxKlient

    @Mock
    private lateinit var fagmodulKlient: FagmodulKlient

    private lateinit var helper: SedDokumentHelper


    @BeforeEach
    fun before() {
        helper = SedDokumentHelper(fagmodulKlient, euxKlient)
    }


    @Test
    fun `Finn korrekt ytelsestype for AP fra sed R005`() {
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-AP.json")))
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = mapOf<String,String?>(SedType.R005.name to sedR005)
        val actual = helper.hentYtelseType(sedHendelse, seds)

        assertEquals(YtelseType.ALDER ,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-UT.json")))
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = mapOf<String,String?>(SedType.R005.name to sedR005)
        val actual = helper.hentYtelseType(sedHendelse, seds)

        assertEquals(YtelseType.UFOREP,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-UT.json")))
        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P15000-NAV.json")))

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "P", bucType = BucType.P_BUC_10, sedType = SedType.P15000)
        val seds = mapOf<String,String?>(SedType.R005.name to sedR005, SedType.P15000.name to sed)
        val actual = helper.hentYtelseType(sedHendelse, seds)

        assertEquals(YtelseType.ALDER ,actual)
    }

    @Test
    fun `henter en map av gyldige seds i buc`() {
        val alldocsid = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val sedP2000 =  String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2000-NAV.json")))

        doReturn(alldocsid).whenever(fagmodulKlient).hentAlleDokumenter(ArgumentMatchers.anyString())
        doReturn(sedP2000).whenever(euxKlient).hentSed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())

        val actual = helper.hentAlleSedIBuc("123123")
        assertEquals(1, actual.size)
        assertEquals(SedType.P2000.name, actual.keys.toList()[0])
        assertEquals(sedP2000, actual[SedType.P2000.name])
        assertEquals(sedP2000, helper.hentAlleSeds(actual)[0])
    }


    @Test
    fun `Gitt det finnes aktierid og det finnes en eller flere pensjonsak Så skal det sakid fra sed valideres og sakid returneres`() {

        val expected = PensjonSak(sakid = 22874955, sakType = YtelseType.ALDER, status = "INNV")
        val mockPensjonSaklist = listOf(expected, PensjonSak(sakid = 22874901, sakType = YtelseType.UFOREP , status = "AVSL"))

        doReturn(mockPensjonSaklist).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())

        val sedP5000 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P5000-medNorskGjenlevende-NAV.json")))
        val sedP2100 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2100-utenNorskGjenlevende-NAV.json")))
        val mockAllSediBuc = mapOf("P2000" to sedP2100, "P5000" to sedP5000)

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)

        assertNotNull(result)
        assertEquals(expected, result)
        assertEquals(expected.toJson(), result?.toJson())

    }

    @Test
    fun `Gitt at det finnes eessisak der land ikke er Norge så returneres null`() {

        val sedP2000 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-ugyldigFNR-NAV.json")))
        val mockAlleSedIBuc = mapOf("P2000" to sedP2000)

        val result = helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc)

       assertNull(result)

    }

    @Test
    fun `Gitt at det finnes eessisak der land er Norge og saksnummer er på feil format så skal null returneres`() {
        val sedP2000 = """
            {
              "nav": {
                "eessisak" : [ {
                  "land" : "NO",
                  "saksnummer" : "ABCDEFGHIJKL"
                } ]
               },
              "sed": "P2000",
              "sedGVer": "4",
              "sedVer": "1"
            }
        """.trimIndent()
        val mockAlleSedIBuc = mapOf("P2000" to sedP2000)
        val result = helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc)
        assertNull(result)
    }

    @Test
    fun `Gitt at det finnes en aktoerid med eessisak der land er Norge når kall til tjenesten feiler så kastes det en exception`() {
        val sedP2000 = """
            {
              "nav": {
                "eessisak" : [ {
                  "land" : "NO",
                  "saksnummer" : "123456"
                } ]
               },
              "sed": "P2000",
              "sedGVer": "4",
              "sedVer": "1"
            }
        """.trimIndent()

        doThrow(RuntimeException()).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())

        val mockAlleSedIBuc = mapOf("P2000" to sedP2000)
        assertThrows<RuntimeException>{
            helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc)
        }
    }

    @Test
    fun `Temp pensjonsaktest`() {
        val sedP2000 = """
            {
              "nav": {
                "eessisak" : [ {
                  "land" : "NO",
                  "saksnummer" : "22874955"
                } ]
               },
              "sed": "P2000",
              "sedGVer": "4",
              "sedVer": "1"
            }
        """.trimIndent()

        val sedP5000 = """
            {
              "nav": {
                "eessisak" : [ {
                  "land" : "NO",
                  "saksnummer" : "22874900"
                } ]
               },
              "sed": "PX000",
              "sedGVer": "4",
              "sedVer": "1"
            }
        """.trimIndent()

        val expected = PensjonSak(sakid = 22874955, sakType = YtelseType.ALDER, status = "INNV")
        val mockPensjonSaklist = listOf(expected, PensjonSak(sakid = 22874901, sakType = YtelseType.UFOREP , status = "AVSL"),
                PensjonSak(sakid = 22874123, sakType = YtelseType.GJENLEV , status = "AVSL"),
                PensjonSak(sakid = 22874456, sakType = YtelseType.BARNEP , status = "AVSL"))

        doReturn(mockPensjonSaklist).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())

        val mockAllSediBuc = mapOf("P2000" to sedP2000, "P4000" to sedP2000, "P5000" to sedP5000, "P6000" to sedP2000)

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)

        assertNotNull(result)
        assertEquals(expected, result)
        assertEquals(expected.toJson(), result?.toJson())

    }

}
