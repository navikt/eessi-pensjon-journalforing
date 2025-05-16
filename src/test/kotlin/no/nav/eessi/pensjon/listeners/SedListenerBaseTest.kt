package no.nav.eessi.pensjon.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SedListenerBaseTest {

    private val fagmodulKlient = mockk<FagmodulKlient>()
    private val fagmodulService = FagmodulService(fagmodulKlient)
    private val bestemSakService = mockk<BestemSakService>(relaxed = true)
    private val gcpStorageService = mockk<GcpStorageService>(relaxed = true)
    private val euxService = mockk<EuxService>(relaxed = true)

    private lateinit var sedListenerBase: SedListenerBase

    @BeforeEach
    fun setup() {
        every { euxService.hentSaktypeType(any(), any()) } returns SakType.ALDER
        every { gcpStorageService.gjennyFinnes(any()) } returns false
        every { bestemSakService.hentSakInformasjonViaBestemSak(any(), any(), any(), any()) } returns null

        sedListenerBase = object : SedListenerBase(
            fagmodulService,
            bestemSakService,
            gcpStorageService,
            euxService,
            "test"
        ) {
            override fun behandleSedHendelse(sedHendelse: SedHendelse, buc: Buc) {}
        }
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
        SENDT, GJENLEV,22975700, true"""
    )
    fun `hentSaksInformasjonForEessi skal gi advarsel der hvor pesys nr fra pesys ikke stemmer med sed pesys nr 22975710`(
        hendelsesType: HendelseType,
        sakTypeFraSed: SakType?,
        sakId: String?,
        expectedAdvarsel: Boolean
    ) {
        every { euxService.hentSaktypeType(any(), any()) } returns sakTypeFraSed
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns createPensjonSakList(sakId)

        hentResultat("P8000_pesysId.json", sakId, hendelsesType).also {
            assertEquals(sakId, it?.sakInformasjonFraPesys?.sakId)
            assertEquals(expectedAdvarsel, it?.advarsel)
        }
    }


    @Nested
    @DisplayName("Inngående sed")
    inner class InngaaendeSed {
        @Test
        fun `gitt flere sakid fra pesys og flere fra sed og match, med første i listen fra sed`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22975710;22970000")
            assertEquals("22975710", resultat?.sakInformasjonFraPesys?.sakId)
        }

        @Test
        fun `gitt flere sakid fra pesys og flere fra sed og match, med andre i listen fra sed`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22975232;22970000")
            assertEquals("22975232", resultat?.sakInformasjonFraPesys?.sakId)
        }

        @Test
        fun `gitt én sakid fra pesys og én sakid i SED og ingen match`() {
            val resultat = hentResultat("P8000_pesysId.json", "22975200")
            assertEquals("22975200", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(true, resultat?.advarsel)
        }

        @Test
        fun `gitt én sakid fra pesys og ingen sakid i SED og ingen match`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", "22975200")
            assertEquals("22975200", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        @Test
        fun `gitt flere sakid fra pesys og vi har match med sakid fra SED`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22975232;22975200")
            assertEquals("22975232", resultat?.sakInformasjonFraPesys?.sakId)
        }

        @Test
        fun `gitt flere sakid fra pesys og flere fra sed og ingen match`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22111111;22222222")
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
        }

        @Test
        fun `gitt flere sakid fra pesys og én sakid i SED og ingen match`() {
            val resultat = hentResultat("P8000_pesysId.json", "22111111;22222222")
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
        }

    }

    private fun loadSed(fileName: String): P8000 {
        val sedJson = javaClass.getResource("/sed/$fileName")!!.readText()
        return mapJsonToAny(sedJson)
    }

    private fun createPensjonSakList(pesysIds: String?): List<SakInformasjon> {
        return pesysIds?.split(";")?.map {
            spyk(SakInformasjon(
                sakId = it,
                sakStatus = SakStatus.LOPENDE,
                sakType = SakType.GJENLEV,
                saksbehandlendeEnhetId = "NFP_UTLAND_AALESUND",
                nyopprettet = false
            ))
        } ?: emptyList()
    }

    private fun mockHentSakList(pesysIds: String?) {
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns createPensjonSakList(pesysIds)
    }

    fun hentResultat(sedFilename: String, pesysIds: String?, hendelsesType: HendelseType = HendelseType.MOTTATT) : SaksInfoSamlet? {
        val sed = loadSed(sedFilename)
        val sedEvent = mockk<SedHendelse> { every { rinaSakId } returns "12345" }
        val identifiedPerson = mockk<IdentifisertPDLPerson> { every { aktoerId } returns "123456799" }

        mockHentSakList(pesysIds)

        return sedListenerBase.hentSaksInformasjonForEessi(
            listOf(sed),
            sedEvent,
            bucType = BucType.P_BUC_10,
            identifiedPerson,
            hendelsesType,
            sed
        )
    }
}