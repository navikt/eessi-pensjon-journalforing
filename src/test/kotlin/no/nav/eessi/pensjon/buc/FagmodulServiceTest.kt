package no.nav.eessi.pensjon.buc

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class FagmodulServiceTest {

    private val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)

    private val helper = FagmodulService(fagmodulKlient)

    @AfterEach
    fun after() {
        confirmVerified(fagmodulKlient)
        clearAllMocks()
    }

    @Test
    fun `Gitt det finnes aktoerid og det finnes en eller flere pensjonsak Så skal det sakid fra sed valideres og sakid returneres`() {

        val expected = SakInformasjon(sakId = "22874955", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
        val mockPensjonSaklist = listOf(expected, SakInformasjon(sakId = "22874901", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET))

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist

        val sedP5000 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P5000-medNorskGjenlevende-NAV.json")))
        val mockAllSediBuc = listOf(
                mapJsonToAny<SED>(sedP5000)
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)

        assertNotNull(result)
        assertEquals(expected.sakId, result?.sakId)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes eessisak der land ikke er Norge så returneres null`() {
        val sedP2000 = javaClass.getResource("/sed/P2000-ugyldigFNR-NAV.json").readText()

        val mockAllSediBuc = listOf(
                mapJsonToAny<SED>(sedP2000)
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)
        assertNull(result)

        verify { fagmodulKlient wasNot Called }
    }

    @Test
    fun `Gitt at det finnes eessisak der land er Norge og saksnummer er på feil format så skal null returneres`() {
        val mockAlleSedIBuc = listOf(
                mockSED(P2000, eessiSakId = "UGYLDIG SAK ID")
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc)
        assertNull(result)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes en aktoerid med eessisak der land er Norge når kall til tjenesten feiler så kastes det en exception`() {
        every { fagmodulKlient.hentPensjonSaklist(any()) } throws RuntimeException()

        val mockAlleSedIBuc = listOf(
                mockSED(P2000, eessiSakId = "12345")
        )

        assertNull(helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc))

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag mot pensjoninformasjon tjenesten, For så å hente ut rett SakInformasjon`() {
        val expected = SakInformasjon(sakId = "22874955", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon(sakId = "22874901", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874123", sakType = Saktype.GJENLEV, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874456", sakType = Saktype.BARNEP, sakStatus = SakStatus.AVSLUTTET))

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist
        val mockAllSediBuc = listOf(
                mockSED(P2000, eessiSakId = "22874955"),
                mockSED(P4000, eessiSakId = "22874955"),
                mockSED(P5000, eessiSakId = "22874955"),
                mockSED(P6000, eessiSakId = "22874955")
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertEquals(3, result.tilknyttedeSaker.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag, hvis sak er GENERELL kan sjekkes om har tilknytteteSaker`() {
        val expected = SakInformasjon(sakId = "22874456", sakType = Saktype.GENRL, sakStatus = SakStatus.LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon(sakId = "22874901", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874123", sakType = Saktype.GJENLEV, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874457", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
        )

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist
        val mockAllSediBuc = listOf(
                mockSED(P2000, eessiSakId = "22874456"),
                mockSED(P4000, eessiSakId = "22874456"),
                mockSED(P5000, eessiSakId = "22874456"),
                mockSED(P6000, eessiSakId = "22874456")
        )

        val result = helper.hentPensjonSakFraSED("aktoerId", mockAllSediBuc)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertTrue(result.harGenerellSakTypeMedTilknyttetSaker())
        assertEquals(3, result.tilknyttedeSaker.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc har forskjellige saknr hents ingen for oppslag, ingen SakInformasjon returneres`() {
        val mockAllSediBuc = listOf(
                mockSED(P2000, eessiSakId = "111"),
                mockSED(P4000, eessiSakId = "222"),
                mockSED(P6000, eessiSakId = "333")
        )

        assertNull(
                helper.hentPensjonSakFraSED("111", mockAllSediBuc),
                "Skal ikke få noe i retur dersom det finnes flere unike EessiSakIDer."
        )

        verify { fagmodulKlient wasNot Called }
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
