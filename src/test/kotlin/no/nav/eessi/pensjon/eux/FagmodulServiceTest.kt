package no.nav.eessi.pensjon.eux

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
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

        val expected = sakInformasjon(sakType = ALDER, sakStatus = LOPENDE)
        val mockPensjonSaklist = listOf(expected, SakInformasjon(sakId = "22874955", sakType = UFOREP, sakStatus = AVSLUTTET))

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist

        val sedP5000 =  mapJsonToAny<SED>(javaClass.getResource("/sed/P5000-medNorskGjenlevende-NAV.json")!!.readText())

        val result = helper.hentPensjonSakFraPesys("123123", listOf(sedP5000), sedP5000)

        assertNotNull(result)
        assertEquals(expected.sakId, result?.sakId)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes eessisak der land ikke er Norge så returneres null`() {
        val sedP2000 = javaClass.getResource("/sed/P2000-ugyldigFNR-NAV.json")!!.readText()
        val mockAllSediBuc = listOf(mapJsonToAny<SED>(sedP2000))

        val result = helper.hentPensjonSakFraPesys("123123", mockAllSediBuc, null)
        assertNull(result)

        verify { fagmodulKlient wasNot Called }
    }

    @Test
    fun `Gitt at det finnes eessisak der land er Norge og saksnummer er på feil format så skal null returneres`() {
        val mockAlleSedIBuc = listOf(mockSED(P2000, eessiSakId = "UGYLDIG SAK ID"))

        val result = helper.hentPensjonSakFraPesys("123123", mockAlleSedIBuc, null)
        assertNull(result)

        verify(exactly = 0) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag mot pensjoninformasjon tjenesten, For så å hente ut rett SakInformasjon`() {
        val expected = sakInformasjon("22874955", ALDER, LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon("22874901", UFOREP, AVSLUTTET),
                SakInformasjon("22874123", GJENLEV, AVSLUTTET),
                SakInformasjon("22874456", BARNEP, AVSLUTTET))

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist
        val mockAllSediBuc = listOf(
                mockSED(P2000, "22874955"),
                mockSED(P4000, "22874955"),
                mockSED(P5000, "22874955"),
                mockSED(P6000, "22874955")
        )

        val result = helper.hentPensjonSakFraPesys("123123", mockAllSediBuc, null)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertEquals(3, result.tilknyttedeSaker.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag, hvis sak er GENERELL kan sjekkes om har tilknytteteSaker`() {
        val expected = sakInformasjon("22874456", GENRL)

        val mockPensjonSaklist = listOf(
            expected,
            sakInformasjon("22874457", ALDER, LOPENDE),
            sakInformasjon("22874901", UFOREP, AVSLUTTET),
            sakInformasjon("22874123", GJENLEV, AVSLUTTET)
        )

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist
        val mockAllSediBuc = listOf(mockSED(P2000), mockSED(P4000), mockSED(P5000), mockSED(P6000))

        val result = helper.hentPensjonSakFraPesys("aktoerId", mockAllSediBuc, null)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertTrue(result.harGenerellSakTypeMedTilknyttetSaker())
        assertEquals(3, result.tilknyttedeSaker.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc har forskjellige saknr hents ingen for oppslag, ingen SakInformasjon returneres`() {
        val mockAllSediBuc = listOf(mockSED(P2000, "111"), mockSED(P4000, "222"), mockSED(P6000, "333"))

        assertNull(
                helper.hentPensjonSakFraPesys("111", mockAllSediBuc, null),
                "Skal ikke få noe i retur dersom det finnes flere unike EessiSakIDer."
        )

        verify { fagmodulKlient wasNot Called }
    }

    private fun sakInformasjon(
        sakId: String? = "22874955",
        sakType: SakType = ALDER,
        sakStatus: SakStatus = LOPENDE
    ) = SakInformasjon(sakId = sakId, sakType = sakType, sakStatus = sakStatus)

    private fun mockSED(sedType: SedType, eessiSakId: String? = "22874456"): SED {
        return SED(
                type = sedType,
                nav = Nav(
                        eessisak = listOf(EessisakItem(saksnummer = eessiSakId, land = "NO"))
                )
        )
    }

}
