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

internal class FagmodulServiceTest {

    private val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    private val fmService = FagmodulService(fagmodulKlient)

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
        val eessisakList = sedP5000.nav?.eessisak?.mapNotNull { it.saksnummer }
        val result = fmService.hentPensjonSakFraPesys("123123", eessisakList, sedP5000)

        assertNotNull(result)
        assertEquals(expected.sakId, result?.first?.sakId)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes eessisak der land ikke er Norge så returneres null`() {
        val sedP2000 = javaClass.getResource("/sed/P2000-ugyldigFNR-NAV.json")!!.readText()
        val mockAllSediBuc = listOf(mapJsonToAny<SED>(sedP2000))
        val eessisakList = fmService.hentPesysSakIdFraSED(mockAllSediBuc, null)
        val result = fmService.hentPensjonSakFraPesys("123123", eessisakList?.second, null)
        assertNull(result?.first)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes eessisak der land er Norge og saksnummer er på feil format så skal null returneres`() {
        val mockAlleSedIBuc = listOf(mockSED(P2000, eessiSakId = "UGYLDIG SAK ID"))
        val eessisakList =  fmService.hentPesysSakIdFraSED(mockAlleSedIBuc, null)
        val result = fmService.hentPensjonSakFraPesys("123123", eessisakList?.second, null)
        assertNull(result?.first)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hentes kun et for oppslag mot pensjoninformasjon tjenesten, For så å hente ut rett SakInformasjon`() {
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
        val eessisakList =  mockAllSediBuc.flatMap { it.nav?.eessisak.orEmpty() }.mapNotNull { it.saksnummer }
        val result = fmService.hentPensjonSakFraPesys("123123", eessisakList, mockAllSediBuc.first())!!
        println("result*****: $result")
        assertNotNull(result)
        assertEquals(expected.sakType, result.first?.sakType)
        assertEquals(3, result.first?.tilknyttedeSaker?.size)

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
        val eessisakList =  mockAllSediBuc.flatMap { it.nav?.eessisak.orEmpty() }.mapNotNull { it.saksnummer }
        val result = fmService.hentPensjonSakFraPesys("aktoerId", eessisakList, mockAllSediBuc.get(0))!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.first?.sakType)
        assertTrue(result.first?.harGenerellSakTypeMedTilknyttetSaker() == true)
        assertEquals(3, result.first?.tilknyttedeSaker?.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc har forskjellige saknr hents ingen for oppslag, ingen SakInformasjon returneres`() {
        val mockAllSediBuc = listOf(mockSED(P2000, "111"), mockSED(P4000, "222"), mockSED(P6000, "333"))
        val eessisakList =  fmService.hentPesysSakIdFraSED(mockAllSediBuc, null)
        val result = fmService.hentPensjonSakFraPesys("111", eessisakList?.second, null)?.first
        assertNull(result, "Skal ikke få noe i retur dersom det finnes flere unike EessiSakIDer.")

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `hentPesysSakIdFraSED skal gi null og tom liste naar SED mangler pesyys sakid`() {
        val sedListe = listOf(mockSED(P2000, eessiSakId = null))
        val currentSed = mockSED(P2000, eessiSakId = null)

        val result = fmService.hentPesysSakIdFraSED(sedListe, currentSed)

        assertNull(result?.first)
        assertEquals(emptyList<SED>(), result?.second)
    }

    @Test
    fun `hentPesysSakIdFraSED skal gi matchende sakId fra pesys naar det matcher listen fra pesys`() {
        val sedListe = listOf(mockSED(P2000, eessiSakId = "12345678"))
        val currentSed = mockSED(P2000, eessiSakId = "12345678")

        val result = fmService.hentPesysSakIdFraSED(sedListe, currentSed)

        assertEquals("12345678", result?.first)
        assertEquals(listOf("12345678"), result?.second)
    }

    @Test
    fun `hentPesysSakIdFraSED skal returnere den første saken fra pesys naar det er flere pesys saksId i SED`() {
        val sedListe = listOf(
            mockSED(P2000, eessiSakId = "12345678"),
            mockSED(P4000, eessiSakId = "87654321")
        )
        val currentSed = mockSED(P2000, eessiSakId = "12345678")

        val result = fmService.hentPesysSakIdFraSED(sedListe, currentSed)

        assertEquals("12345678", result?.first)
        assertEquals(listOf("12345678"), result?.second)
    }

    @Test
    fun `hentPesysSakIdFraSED filterer bort ugyldige nummer og gir kun korrekte tilbake`() {
        val sedListe = listOf(
            mockSED(P2000, eessiSakId = "12345678"),
            mockSED(P4000, eessiSakId = "INVALID"),
            mockSED(P5000, eessiSakId = "87654321")
        )
        val result = fmService.hentPesysSakIdFraSED(sedListe, sedListe.first())

        assertEquals("12345678", result?.first)
        assertEquals(listOf("12345678"), result?.second)
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
