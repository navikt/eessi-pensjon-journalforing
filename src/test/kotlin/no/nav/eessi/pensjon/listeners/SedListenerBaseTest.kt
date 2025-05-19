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

    /**
     * Regler: https://confluence.adeo.no/spaces/EP/pages/704513683/Dato+endring+i+PROD+06.05.2025+11+25
     * PesysSakId er herunder kalt sakId
     */
    @Nested
    @DisplayName("Inngående sed")
    inner class InngaaendeSed {
        //1. Automatiskk journalføring med opprettelse av BEHANDLE_SED oppgave
        @Test
        fun `Gitt at det finnes én sakId i SED og det finnes én sakId fra PenInfo men INGEN match saa skal det SENDES ADVARSEL`() {
            val resultat = hentResultat("P8000_pesysId.json", "22111111")
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(null, resultat?.saksIdFraSed)
            assertEquals(true, resultat?.advarsel)
        }

        //2. Automatiskk journalføring med opprettelse av BEHANDLE_SED oppgave
        @Test
        fun `Gitt at det finnes flere sakider fra SED og flere sakid fra pesys med en MATCH på første i listen fra sed saa skal det IKKE sendes advarsel`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22975710;22970000")
            assertEquals("22975710", resultat?.saksIdFraSed)
            assertEquals("22975710", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //3. Automatiskk journalføring med opprettelse av BEHANDLE_SED oppgave
        @Test
        fun `Gitt at det er flere sakider i SED og flere sakIder fra PenInfo med MATCH så skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22975232;22970000")
            assertEquals("22975232", resultat?.saksIdFraSed)
            assertEquals("22975232", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //4. Manuell journalføring (Oppretter JFR_INN oppgave)
        @Test
        fun `Gitt at det finnes flere sakIder i SED og det finnes flere sakIder fra PenInfo men INGEN MATCH saa skal det SENDES ADVARSEL`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22111111;22222222")
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(true, resultat?.advarsel)
        }

        //5. Manuell journalføring (Oppretter JFR_INN oppgave) MED ADVARSEL
        @Test
        fun `Gitt at gitt at IKKE finnes sakId i SED og det IKKE finnes sakId fra PenInfo saa skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", null)
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //6. Manuell journalføring (Oppretter JFR_INN oppgave) MED ADVARSEL
        @Test
        fun `Gitt at det finnes IKKE sakid i SED og det finnes flere sakIder fra PenInfo men INGEN MATCH saa skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", "22975200;22970000")
            assertEquals("22975200", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //7. Automatisk journalføring (Oppretter BEHANDLE_SED oppgave)
        @Test
        fun `Gitt ingen sakId fra SED og én sakId i svar fra PenInfo og INGEN MATCH saa skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", "22975200")
            assertEquals("22975200", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        @Test
        fun `gitt flere sakid fra pesys og vi har MATCH med sakid fra SED`() {
            val resultat = hentResultat("P8000_flere_pesysId.json", "22975232;22975200")
            assertEquals("22975232", resultat?.saksIdFraSed)
            assertEquals("22975232", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }


        @Test
        fun `gitt flere sakid fra pesys og én sakid i SED og INGEN MATCH`() {
            val resultat = hentResultat("P8000_pesysId.json", "22111111;22222222")
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(true, resultat?.advarsel)
        }

    }

    @Nested
    @DisplayName("Utgående sed")
    inner class UtgaaendeSed {

        //8. Manuell journalføring INGEN advarsel
        @Test
        fun `Gitt at det ikke finnes sakId fra SED med det ikke finnes sakId fra PenInfo saa skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", null, hendelsesType = HendelseType.SENDT)
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //9. Manuell journalføring ADVARSEL sendes
        @Test
        fun `Gitt at det finnes flere sakId fra SED én sakid fra PenInfo INGEN match saa skal det SENDES ADVARSEL`() {
            val resultat = hentResultat("P8000_pesysId.json", "22111111", hendelsesType = HendelseType.SENDT)
            assertEquals("22111111", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(true, resultat?.advarsel)
        }

        //10. Automatisk journalføring Ingen ADVARSEL
        @Test
        fun `Gitt at det ikke finnes sakId i SED men finnes én sakId fra PenInfo og INGEN MATCH så blir det IKKE sendt ADVARSEL`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", "22111111", hendelsesType = HendelseType.SENDT)
            assertEquals("22111111", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //11. Automatisk journalføring Ingen ADVARSEL
        @Test
        fun `Gitt at det ikke finnes saksId i SED men flere sakider fra PenInfo og INGEN MATCH saa skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", "22111111;22222222", hendelsesType = HendelseType.SENDT)
            assertEquals("22111111", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        //12. Automatisk journalføring Ingen ADVARSEL
        @Test
        fun `Gitt at det finnes flere saksIder i SED og flere sakIder fra PenInfo med MATCH saa skal det IKKE sendes ADVARSEL`() {
            val resultat = hentResultat("P8000_pesysId.json", "22975710;22222222", hendelsesType = HendelseType.SENDT)
            assertEquals("22975710", resultat?.saksIdFraSed)
            assertEquals("22975710", resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

        @Test
        fun `gitt ingen sakid fra pesys og ingen SED og INGEN MATCH så blir det advarsel`() {
            val resultat = hentResultat("P8000_ingen_pesysId.json", null, hendelsesType = HendelseType.SENDT)
            assertEquals(null, resultat?.sakInformasjonFraPesys?.sakId)
            assertEquals(false, resultat?.advarsel)
        }

    }

    private fun createPensjonSakList(pesysIds: String?): List<SakInformasjon> {
        return pesysIds?.split(";")?.mapIndexed { index, it ->
            spyk(SakInformasjon(
                sakId = it,
                sakStatus = SakStatus.LOPENDE,
                sakType = if (index == 0) SakType.ALDER else SakType.UFOREP,
                saksbehandlendeEnhetId = "NFP_UTLAND_AALESUND",
                nyopprettet = false
            ))
        } ?: emptyList()
    }

    private fun mockHentSakList(pesysIds: String?) {
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns createPensjonSakList(pesysIds)
    }

    fun hentResultat(sedFilename: String, pesysIds: String?, hendelsesType: HendelseType = HendelseType.MOTTATT) : SaksInfoSamlet? {
        val sedJson = javaClass.getResource("/sed/$sedFilename")!!.readText()
        val sed = mapJsonToAny<P8000>(sedJson)
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