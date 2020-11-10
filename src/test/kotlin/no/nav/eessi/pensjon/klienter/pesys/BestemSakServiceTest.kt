package no.nav.eessi.pensjon.klienter.pesys

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.http.ResponseEntity

internal class BestemSakServiceTest {

    private val mockKlient: BestemSakKlient = mockk()
    private val bestemSakService = BestemSakService(mockKlient)

    companion object {
        private const val AKTOER_ID = "12345678901"
    }

    @Test
    fun `Gitt en P_BUC_01 med kjent aktørId når journalføring utføres så kall bestemSak`() {
        val response = opprettResponse("pen/bestemSakResponse.json")

        val requestSlot = slot<BestemSakRequest>()
        every { mockKlient.kallBestemSak(capture(requestSlot)) } returns response

        val actualResponse = bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_01, null)!!
        assertEquals("22873157", actualResponse.sakId)

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(YtelseType.ALDER, actualRequest.ytelseType)
        assertNotNull(actualRequest.callId)
        assertNotNull(actualRequest.consumerId)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt en P_BUC_02 med gjenlevende en kjent aktørId og toggle er på skal det returneres et saksnummer`() {
        val response = opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        val requestSlot = slot<BestemSakRequest>()
        every { mockKlient.kallBestemSak(capture(requestSlot)) } returns response

        val actualResponse = bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.GJENLEV)!!
        assertEquals("22873157", actualResponse.sakId)

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(YtelseType.GJENLEV, actualRequest.ytelseType)
        assertNotNull(actualRequest.callId)
        assertNotNull(actualRequest.consumerId)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt en P_BUC_02 med barnep en kjent aktørId og toggle er på skal det returneres et saksnummer`() {
        val sakInformasjonListe = listOf(SakInformasjon("2345678975414", YtelseType.BARNEP, SakStatus.LOPENDE, "4808", true))

        every { mockKlient.kallBestemSak(any()) } returns BestemSakResponse(sakInformasjonListe = sakInformasjonListe)

        val result = bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.BARNEP)
        assertEquals("2345678975414", result?.sakId)
        assertEquals(true, result?.nyopprettet)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt en P_BUC_02 med UFOREP med en kjent aktørId så skal det returneres et saksnummer og sakstype UFOREP`() {
        val sakInformasjonListe = listOf(SakInformasjon("2345678975414", YtelseType.UFOREP, SakStatus.LOPENDE, "4808", false))

        every { mockKlient.kallBestemSak(any()) } returns BestemSakResponse(sakInformasjonListe = sakInformasjonListe)

        val expectedSakInformasjon = SakInformasjon("2345678975414", YtelseType.UFOREP, SakStatus.LOPENDE, "4808", false)

        assertEquals(expectedSakInformasjon.toJson(), bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.GJENLEV)?.toJson())

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt en P_BUC_02 med ukjent ytelse og en kjent aktørId så skal det returneres null`() {
        assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, null))
    }

    @Test
    fun `Gitt at vi har en P_BUC_02 med uførep og en med Alder og aktørId så skal enhet behandlende enhet null returneres`() {
        val sakInformasjonListe = listOf(
                SakInformasjon("2345678975414", YtelseType.UFOREP, SakStatus.AVSLUTTET, "4476", false),
                SakInformasjon("2345678975414", YtelseType.ALDER, SakStatus.LOPENDE, "4303", false)
        )

        every { mockKlient.kallBestemSak(any()) } returns BestemSakResponse(sakInformasjonListe = sakInformasjonListe)

        assertEquals(null, bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.GJENLEV)?.saksbehandlendeEnhetId)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt at vi har en P_BUC_02 med uførep og aktørId så skal enhet behandlende enhet 4476 returneres`() {
        val sakInformasjonListe = listOf(
                SakInformasjon("2345678975414", YtelseType.UFOREP, SakStatus.LOPENDE, "4476", false)
        )

        every { mockKlient.kallBestemSak(any()) } returns BestemSakResponse(sakInformasjonListe = sakInformasjonListe)

        assertEquals("4476", bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.GJENLEV)?.saksbehandlendeEnhetId)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt at vi har en P_BUC_02 med kjent aktørId bosatt utland så skal behandlende enhet 0001 returneres`() {
        val sakInformasjonListe = listOf(
                SakInformasjon("2345678975414", YtelseType.BARNEP, SakStatus.LOPENDE, "0001", false)
        )

        every { mockKlient.kallBestemSak(any()) } returns BestemSakResponse(sakInformasjonListe = sakInformasjonListe)

        val response = bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.GJENLEV)!!
        assertEquals("0001", response.saksbehandlendeEnhetId)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    fun opprettResponse(file: String): BestemSakResponse {
        val responseBody = javaClass.classLoader.getResource(file)!!.readText()

        return mapJsonToAny(responseBody, typeRefs())
    }

}
