package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteResponseData
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.shared.person.FodselsnummerGenerator
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JournalforingServiceHentSakTest : JournalforingServiceBase() {

    @Test
    fun `hentSak skal gi sak ved treff mot tidligere gjennySak`() {
        val euxCaseId = "123"
        val gjennySakJson = """{ "sakId": "12345", "sakType": "EY"}""".trimIndent()
        val gjennySakJsonEtterlatte = """{ "sakId": "12345", "sakType": "BARNEPENSJON"}""".trimIndent()

        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns gjennySakJson
        every { etterlatteService.hentGjennySak(eq("12345")) } returns  Result.success(mapJsonToAny<EtterlatteResponseData>(gjennySakJsonEtterlatte))

        val result = hentSakService.hentSak(euxCaseId, identifisertPersonFnr = fnr)

        assertEquals(Sak("FAGSAK", "12345", "EY"), result)
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `Gitt at vi oppretter en ny gjennysak og lagrer i bucket saa skal det returneres null ved tom saksid i gjennybucket`() {
        val euxCaseId = "1234567"
        val gjennySakJson = """{ "sakId": "", "sakType": "EY"}""".trimIndent()
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns gjennySakJson
        val result = hentSakService.hentSak(euxCaseId, identifisertPersonFnr = fnr)

        assertNull(result)
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `hentSak skal returnere null det finnes gjennysak men bruker er null`() {
        val euxCaseId = "123"
        val gjennySakJson = """{ "sakId": "sakId123", "sakType": "EY"}""".trimIndent()

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns gjennySakJson

        assertNull(hentSakService.hentSak(euxCaseId))

        verify (exactly = 0) { gcpStorageService.hentFraGjenny(euxCaseId) }
    }
    @Test
    fun `hentSak skal gi Sak fra sakIdFraSed naar gjennySak mangler`() {
        val euxCaseId = "123"
        val sakIdFraSed = "12131132"
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns null
        every { etterlatteService.hentGjennySak(eq(sakIdFraSed)) } returns JournalforingTestBase.mockHentGjennySakMedError()

        val result = hentSakService.hentSak(euxCaseId, sakIdFraSed, identifisertPersonFnr = fnr)

        assertEquals(Sak("FAGSAK", sakIdFraSed, "PP01"), result)
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `hentSak skal returnere null dersom buc finnes i gcpstorage`() {
        val euxCaseId = "123"
        val sakIdFraSed = "12131132"
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))
        val gjennygcpsvar ="""
            {
              "sakId" : null,
              "sakType" : "OMSORG"
            }
        """.trimIndent()

        every { gcpStorageService.hentFraGjenny(any()) } returns gjennygcpsvar
        every { etterlatteService.hentGjennySak(eq(sakIdFraSed)) } returns JournalforingTestBase.mockHentGjennySakMedError()

        val result = hentSakService.hentSak(euxCaseId, sakIdFraSed, identifisertPersonFnr = fnr)

        assertNull(result)
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `hentSak skal gi Sak fra sakInformasjon naar gjennySak og sakIdFraSed mangler`() {
        val euxCaseId = "123"
        val sakInformasjon = SakInformasjon("12131223", SakType.GJENLEV, LOPENDE)
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns null

        val result = hentSakService.hentSak(euxCaseId, sakInformasjon = sakInformasjon, identifisertPersonFnr = fnr)

        assertEquals(Sak("FAGSAK", sakInformasjon.sakId!!, "PP01"), result)
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `hentSak skal gi null naar alt mangler `() {
        val euxCaseId = "123"
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns null

        val result = hentSakService.hentSak(euxCaseId, identifisertPersonFnr = fnr)

        assertNull(result)
        verify { gcpStorageService.hentFraGjenny(euxCaseId) }
    }

    @Test
    fun `hentSak skal gi null naar id fra sakInformasjon er 000000000000 `() {
        val euxCaseId = "123"
        val sakInformasjon = SakInformasjon("00000000000", SakType.GJENLEV, LOPENDE)
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns null

        val result = hentSakService.hentSak(euxCaseId, sakInformasjon = sakInformasjon, identifisertPersonFnr = fnr)

        assertNull(result)
    }

    @Test
    fun `hentSak skal gi null naar sakIdFraSed er 000000000000 `() {
        val euxCaseId = "123"
        val sakIdFraSed = "000000000000"
        val fnr = Fodselsnummer.fra(FodselsnummerGenerator.generateFnrForTest(20))

        every { etterlatteService.hentGjennySak(eq(sakIdFraSed)) } returns JournalforingTestBase.mockHentGjennySakMedError()
        every { gcpStorageService.hentFraGjenny(euxCaseId) } returns null

        val result = hentSakService.hentSak(euxCaseId, sakIdFraSed, identifisertPersonFnr = fnr)

        assertNull(result)
    }
}