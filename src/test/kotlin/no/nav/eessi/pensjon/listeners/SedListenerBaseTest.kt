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
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
        SENDT, P_BUC_10, GJENLEV, 22975700, true
        MOTTATT, P_BUC_10, GJENLEV, 22975700, true
        MOTTATT, P_BUC_10, GJENLEV, 22975710, false
        MOTTATT, P_BUC_10, ALDER, 22975710, false
        MOTTATT, P_BUC_10, ALDER, null, true"""
    )
    fun `hentSaksInformasjonForEessi skal gi advarsel der hvor pesys nr fra pesys ikke stemmer med sed pesys nr 22975710`(
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

    @DisplayName("Vurder om pesys sakId er gyldig gitt flere pesys sak id fra Pesys: ")
    @ParameterizedTest
    @CsvSource(
        "22975232, true, true, '22975710;22975232'",
        "22223332, false, true, '22975710;22975232'",
        ", false, false, '22975710;22975232'"
    )
    fun testHentSaksInformasjonForEessi(sakIdFraPesys: String?, harSakIdFraSed: Boolean, harPesysSakId: Boolean, pesysIdListe: String) {
        val sed = mapJsonToAny<P8000>(javaClass.getResource("/sed/P8000_flere_pesysId.json")!!.readText())
        val alleSedIBucList = listOf(sed)

        val sedHendelse = mockk<SedHendelse> { every { rinaSakId } returns "12345" }
        val identifisertPerson = mockk<IdentifisertPDLPerson> { every { aktoerId } returns "123456799" }

        every { euxService.hentSaktypeType(any(), any()) } returns SakType.ALDER

        val saksInfo = if (!sakIdFraPesys.isNullOrEmpty()) {
            listOf(mockk<SakInformasjon>().apply {
                every { sakId } returns sakIdFraPesys
                every { sakStatus } returns SakStatus.LOPENDE
                every { sakType } returns SakType.GJENLEV
                every { saksbehandlendeEnhetId } returns "NFP_UTLAND_AALESUND"
                every { nyopprettet } returns false
            })
        } else {
            emptyList()
        }
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns saksInfo

        val result = sedListenerBase.hentSaksInformasjonForEessi(
            alleSedIBucList,
            sedHendelse,
            BucType.P_BUC_10,
            identifisertPerson,
            HendelseType.MOTTATT,
            sed
        )

        val listeAvPesysId = pesysIdListe.split(";")

        assertEquals(if (harSakIdFraSed) "22975232" else null, result.saksIdFraSed)
        assertEquals(if (harPesysSakId) sakIdFraPesys else null, result.sakInformasjonFraPesys?.sakId)
     }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
        ID FRA PENSJON (PESYS), RESULTAT,         SED FILNAVN
        '22975232;22975200',    22975232,         P8000_flere_pesysId.json      // sed id matcher pesys; velger første valgte nummer
        '22111111;22222222',    null,             P8000_flere_pesysId.json      // ingen match, flere pesys id og flere sed id; ikke nok informasjon til å ta et bra val
        '22111111;22222222',    null,             P8000_pesysId.json            // ingen match, flere pesys og enkel sed id; ikke nok informasjon til å ta et bra val
        '22975200',             22975200,         P8000_pesysId.json            // ingen match, mem svar (1) fra pesys: velger dette
        '22975200',             22975200,         P8000_ingen_pesysId.json""")  // ingen match, mangler id i sed, men svar(1) fra pesys: velger dette
    fun `hentSaksInformasjonForEessi hvor det er flere pesysid fra sed og pesys`(sakIdFraPesys: String?, valgtPesysId: String?, sedFil: String) {
        val sed = mapJsonToAny<P8000>(javaClass.getResource("/sed/$sedFil")!!.readText())
        val sedHendelse = mockk<SedHendelse> { every { rinaSakId } returns "12345" }
        val identifisertPerson = mockk<IdentifisertPDLPerson> { every { aktoerId } returns "123456799" }

        every { euxService.hentSaktypeType(any(), any()) } returns SakType.ALDER
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns sakIdFraPesys?.split(";")?.map { pesysId ->
            spyk(SakInformasjon(
                sakId = pesysId,
                sakStatus = SakStatus.LOPENDE,
                sakType = SakType.GJENLEV,
                saksbehandlendeEnhetId = "NFP_UTLAND_AALESUND",
                nyopprettet = false
            ))
        }!!

        val result = sedListenerBase.hentSaksInformasjonForEessi(
            listOf(sed),
            sedHendelse,
            BucType.P_BUC_10,
            identifisertPerson,
            HendelseType.MOTTATT,
            sed
        )
        assertEquals(valgtPesysId?.takeIf { it != "null" }, result.sakInformasjonFraPesys?.sakId)
    }

}