package no.nav.eessi.pensjon.buc

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.EessisakItem
import no.nav.eessi.pensjon.models.sed.Nav
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        val sedR005 = javaClass.getResource("/sed/R_BUC_02-R005-AP.json").readText()
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = listOf(mapJsonToAny(sedR005, typeRefs<SED>()))
        val actual = helper.hentYtelseType(sedHendelse, seds)

        assertEquals(YtelseType.ALDER ,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = javaClass.getResource("/sed/R_BUC_02-R005-UT.json").readText()
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = listOf(mapJsonToAny(sedR005, typeRefs<SED>()))

        val actual = helper.hentYtelseType(sedHendelse, seds)
        assertEquals(YtelseType.UFOREP, actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedR005 = javaClass.getResource("/sed/R_BUC_02-R005-UT.json").readText()
        val sed = javaClass.getResource("/buc/P15000-NAV.json").readText()

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "P", bucType = BucType.P_BUC_10, sedType = SedType.P15000)
        val seds: List<SED> = listOf(
                mapJsonToAny(sedR005, typeRefs()),
                mapJsonToAny(sed, typeRefs())
        )

        val actual = helper.hentYtelseType(sedHendelse, seds)
        assertEquals(YtelseType.ALDER, actual)
    }

    @Test
    fun `henter en map av gyldige seds i buc`() {
        val allDocsJson = javaClass.getResource("/fagmodul/alldocumentsids.json").readText()
        val alldocsid = mapJsonToAny(allDocsJson, typeRefs<List<Document>>())

        val sedJson = javaClass.getResource("/buc/P2000-NAV.json").readText()
        val sedP2000 = mapJsonToAny(sedJson, typeRefs<SED>())

        doReturn(alldocsid).whenever(fagmodulKlient).hentAlleDokumenter(ArgumentMatchers.anyString())
        doReturn(sedP2000).whenever(euxKlient).hentSed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())

        val actual = helper.hentAlleSedIBuc("123123")
        assertEquals(1, actual.size)

        val actualSed = actual.first()
        assertEquals(SedType.P2000, actualSed.type)
    }


    @Test
    fun `Gitt det finnes aktoerid og det finnes en eller flere pensjonsak Så skal det sakid fra sed valideres og sakid returneres`() {

        val expected = SakInformasjon(sakId = "22874955", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE)
        val mockPensjonSaklist = listOf(expected, SakInformasjon(sakId = "22874901", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET))

        doReturn(mockPensjonSaklist).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())

        val sedP5000 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P5000-medNorskGjenlevende-NAV.json")))
        val mockAllSediBuc = listOf(
                mapJsonToAny(sedP5000, typeRefs<SED>())
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)

        assertNotNull(result)
        assertEquals(expected.sakId, result?.sakId)
    }

    @Test
    fun `Gitt at det finnes eessisak der land ikke er Norge så returneres null`() {
        val sedP2000 = javaClass.getResource("/sed/P2000-ugyldigFNR-NAV.json").readText()

        val mockAllSediBuc = listOf(
                mapJsonToAny(sedP2000, typeRefs<SED>())
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)
        assertNull(result)
    }

    @Test
    fun `Gitt at det finnes eessisak der land er Norge og saksnummer er på feil format så skal null returneres`() {
        val mockAlleSedIBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "UGYLDIG SAK ID")
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc)
        assertNull(result)
    }

    @Test
    fun `Gitt at det finnes en aktoerid med eessisak der land er Norge når kall til tjenesten feiler så kastes det en exception`() {
        doThrow(RuntimeException()).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())

        val mockAlleSedIBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "12345")
        )

        assertNull(helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc))
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag mot pensjoninformasjon tjenesten, For så å hente ut rett SakInformasjon`() {
        val expected = SakInformasjon(sakId = "22874955", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon(sakId = "22874901", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874123", sakType = YtelseType.GJENLEV, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874456", sakType = YtelseType.BARNEP, sakStatus = SakStatus.AVSLUTTET))

        doReturn(mockPensjonSaklist).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())
        val mockAllSediBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "22874955"),
                mockSED(SedType.P4000, eessiSakId = "22874955"),
                mockSED(SedType.P5000, eessiSakId = "22874955"),
                mockSED(SedType.P6000, eessiSakId = "22874955")
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertEquals(3, result.tilknyttedeSaker.size)
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag, hvis sak er GENERELL kan sjekkes om har tilknytteteSaker`() {
        val expected = SakInformasjon(sakId = "22874456", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon(sakId = "22874901", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874123", sakType = YtelseType.GJENLEV, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874457", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE)
        )

        doReturn(mockPensjonSaklist).whenever(fagmodulKlient).hentPensjonSaklist(ArgumentMatchers.anyString())
        val mockAllSediBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "22874456"),
                mockSED(SedType.P4000, eessiSakId = "22874456"),
                mockSED(SedType.P5000, eessiSakId = "22874456"),
                mockSED(SedType.P6000, eessiSakId = "22874456")
        )

        val result = helper.hentPensjonSakFraSED("aktoerId", mockAllSediBuc)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertTrue(result.harGenerellSakTypeMedTilknyttetSaker())
        assertEquals(3, result.tilknyttedeSaker.size)
    }

    @Test
    fun `Gitt flere sed i buc har forskjellige saknr hents ingen for oppslag, ingen SakInformasjon returneres`() {
        val mockAllSediBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "111"),
                mockSED(SedType.P4000, eessiSakId = "222"),
                mockSED(SedType.P6000, eessiSakId = "333")
        )

        assertNull(
                helper.hentPensjonSakFraSED("111", mockAllSediBuc),
                "Skal ikke få noe i retur dersom det finnes flere unike EessiSakIDer."
        )
        verifyZeroInteractions(fagmodulKlient)
    }

    private fun SedType.opprettJson(saksnummer: String): String {
        return """
            {
              "nav": {
                "eessisak" : [ {
                  "land" : "NO",
                  "saksnummer" : "$saksnummer"
                } ]
               },
              "sed": "${this.name}",
              "sedGVer": "4",
              "sedVer": "1"
            }
        """.trimIndent()
    }

    private fun mockSED(sedType: SedType, eessiSakId: String?): SED {
        return SED(
                type = sedType,
                nav = Nav(
                        eessisak = listOf(EessisakItem(saksnummer = eessiSakId, land = "NO"))
                )
        )
    }

}
