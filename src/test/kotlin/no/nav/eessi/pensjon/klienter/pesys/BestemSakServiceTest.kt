package no.nav.eessi.pensjon.klienter.pesys

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

        val actualResponse = bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_01, null)!!
        assertEquals("22873157", actualResponse.sakId)

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(ALDER, actualRequest.ytelseType)
        assertNotNull(actualRequest.callId)
        assertNotNull(actualRequest.consumerId)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Gitt en P_BUC_02 med ukjent ytelse og en kjent aktørId så skal det returneres null`() {
        assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_02, null))

        verify(exactly = 0) { mockKlient.kallBestemSak(any()) }
    }

    @ParameterizedTest
    @EnumSource(SakType::class, names = ["BARNEP", "GJENLEV", "ALDER", "UFOREP"])
    fun `P_BUC_05 med valgt saktype, skal sende den valgte ytelsestypen`( saktype: SakType) {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_05, saktype)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(saktype, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `P_BUC_02 med valgt saktype, skal sende den valgte ytelsestypen`() {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_05, GJENLEV)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(GJENLEV, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `P_BUC_03 skal sende saktype UFOREP uavhenig av valgt saktype`() {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_03, GENRL)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(UFOREP, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `R_BUC_02 hvor ytelsestype er satt skal returnere den satte typen`() {
        val requestSlot = slot<BestemSakRequest>()
        every {
            mockKlient.kallBestemSak(capture(requestSlot))
        } returns opprettResponse("pen/bestemSakGjenlevendeResponse.json")

        bestemSakService.hentSakInformasjon(AKTOER_ID, R_BUC_02, GENRL)!!

        val actualRequest = requestSlot.captured
        assertEquals(AKTOER_ID, actualRequest.aktoerId)
        assertEquals(GENRL, actualRequest.ytelseType)

        verify(exactly = 1) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `R_BUC_02 hvor ytelsestype er null skal kaste exception`() {
        assertThrows<NullPointerException> {
            bestemSakService.hentSakInformasjon(AKTOER_ID, R_BUC_02, innkommendeSakType = null)!!
        }

        verify(exactly = 0) { mockKlient.kallBestemSak(any()) }
    }

    @Test
    fun `Ugyldig BucType gir null i returverdi`() {
        assertAll(
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_04)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_05)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_06)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_07)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_08)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_09)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, P_BUC_10)) },
                { assertNull(bestemSakService.hentSakInformasjon(AKTOER_ID, H_BUC_07)) }
        )
    }

    private fun opprettResponse(file: String): BestemSakResponse {
        val responseBody = javaClass.classLoader.getResource(file)!!.readText()

        return mapJsonToAny(responseBody)
    }

}
