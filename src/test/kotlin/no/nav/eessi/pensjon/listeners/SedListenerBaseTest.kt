package no.nav.eessi.pensjon.listeners

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.Sak
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SedListenerBaseTest {

    private val fagmodulKlient = mockk<FagmodulKlient>(relaxed = true)
    private val fagmodulService = FagmodulService(fagmodulKlient)
    private val bestemSakService = mockk<BestemSakService>(relaxed = true)
    private val gcpStorageService = mockk<GcpStorageService>(relaxed = true)
    private val euxService = mockk<EuxService>(relaxed = true)

    private lateinit var sedListenerBase: SedListenerBase

    @BeforeEach
    fun setup() {

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
    @CsvSource(
        "SENDT, P_BUC_10, GJENLEV,22975700, true",
        "MOTTATT, P_BUC_10, GJENLEV, 22975700, true",
        "MOTTATT, P_BUC_10, GJENLEV, 22975710, false",
        "MOTTATT, P_BUC_10, ALDER, 22975710, false",
        "MOTTATT, P_BUC_10, ALDER, null, true"
    )
    fun `hentSaksInformasjonForEessi returns correct SaksInfoSamlet based on input`(
        hendelseType: HendelseType,
        bucType: BucType,
        sakTypeFraSed: SakType?,
        sakId: String?,
        expectedAdvarsel: Boolean
    ) {
        val sed = mapJsonToAny<P8000>(javaClass.getResource("/sed/P8000_pesysId.json")!!.readText())
        val alleSedIBucList = listOf<SED>(sed)

        val sedHendelse = mockk<SedHendelse> { every { rinaSakId } returns "12345" }
        val identifisertPerson = mockk<IdentifisertPDLPerson> { every { aktoerId } returns "123456799" }
        val saksInfo = listOf(SakInformasjon(
            sakId = sakId,
            sakStatus = SakStatus.LOPENDE,
            sakType = SakType.GJENLEV,
            saksbehandlendeEnhetId = "NFP_UTLAND_AALESUND",
            nyopprettet = false
        ))

        every { euxService.hentSaktypeType(any(), any()) } returns sakTypeFraSed
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns saksInfo

        val result = sedListenerBase.hentSaksInformasjonForEessi(
            alleSedIBucList,
            sedHendelse,
            bucType,
            identifisertPerson,
            hendelseType,
            sed
        )

        assertEquals(expectedAdvarsel, result.advarsel)
    }
}