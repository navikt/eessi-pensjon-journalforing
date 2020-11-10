package no.nav.eessi.pensjon.klienter.pesys

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.YtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

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
    fun `Gitt en P_BUC_02 med ukjent ytelse og en kjent aktørId så skal det returneres null`() {
        assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, null))

        verify(exactly = 0) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `P_BUC_02 med valgt ytelsetype, skal sende den valgte ytelsestypen`() {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_02, YtelseType.GJENLEV)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(YtelseType.GJENLEV, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `P_BUC_03 skal sende YtelseType UFOREP uavhenig av valgt ytelsetype`() {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_03, YtelseType.GENRL)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(YtelseType.UFOREP, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `R_BUC_02 hvor ytelsestype er satt skal returnere den satte typen`() {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.R_BUC_02, YtelseType.GENRL)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(YtelseType.GENRL, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `R_BUC_02 hvor ytelsestype er null skal kaste exception`() {
        assertThrows<NullPointerException> {
            bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.R_BUC_02, ytelsesType = null)!!
        }

        verify(exactly = 0) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Ugyldig BucType gir null i returverdi`() {
        assertAll(
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_04)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_05)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_06)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_07)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_08)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_09)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.P_BUC_10)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.H_BUC_07)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, BucType.UKJENT)) }
        )
    }

    private fun opprettResponse(file: String): BestemSakResponse {
        val responseBody = javaClass.classLoader.getResource(file)!!.readText()

        return mapJsonToAny(responseBody, typeRefs())
    }

}
