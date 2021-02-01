package no.nav.eessi.pensjon.klienter.norg2

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Enhet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class Norg2ServiceTest {

    private val norg2Klient = mockk<Norg2Klient>()

    private val service = Norg2Service(norg2Klient)

    @Test
    fun `finn fordeligsenhet for utland`() {
        val enheter =  getJsonFileFromResource("/norg2/norg2arbeidsfordelig0001result.json")

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0107"
        )

        val expected = "0001"
        val actual = service.finnArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for utland feiler`() {
        val enheter = listOf<Norg2ArbeidsfordelingItem>()

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0107"
        )

        val actual = service.finnArbeidsfordelingEnheter(request, list = enheter)

        assertNull(actual)
    }

    @Test
    fun `finn fordeligsenhet for Oslo`() {
        val enheter = getJsonFileFromResource("/norg2/norg2arbeidsfordelig4803result.json")

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0104"
        )

        val expected = "4803"
        val actual = service.finnArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `finn fordeligsenhet for Aalesund`() {
        val enheter =  getJsonFileFromResource("/norg2/norg2arbeidsfordelig4862result.json")

        val request = Norg2ArbeidsfordelingRequest(
                geografiskOmraade = "ANY",
                behandlingstype = "ae0104"
        )

        val expected = "4862"
        val actual = service.finnArbeidsfordelingEnheter(request, list = enheter)

        assertEquals(expected, actual)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Norg2 avd Oslo`() {
        val response = getJsonFileFromResource("/norg2/norg2arbeidsfordelig4803result.json")
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns response

        val result = service.hentArbeidsfordelingEnhet(
            NorgKlientRequest(landkode = "NOR", geografiskTilknytning = "0422")
        )

        assertEquals(Enhet.NFP_UTLAND_OSLO, result)
    }

    @Test
    fun `hent arbeidsfordeligEnheter fra Utland`() {
        val response = getJsonFileFromResource("/norg2/norg2arbeidsfordelig0001result.json")
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns response

        val result = service.hentArbeidsfordelingEnhet(NorgKlientRequest())

        assertEquals(Enhet.PENSJON_UTLAND, result)
    }

    @Test
    fun `hent arbeidsfordeligEnheter ved diskresjon`() {
        val response = getJsonFileFromResource("/norg2/norg2arbeidsfordeling2103result.json")
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns response


        val result = service.hentArbeidsfordelingEnhet(
            NorgKlientRequest(landkode = "NOR", harAdressebeskyttelse = true)
        )

        assertEquals(Enhet.DISKRESJONSKODE, result)
    }

    private fun getJsonFileFromResource(filename: String): List<Norg2ArbeidsfordelingItem> {
        val json = javaClass.getResource(filename).readText()

        return mapJsonToAny(json, typeRefs())
    }

}
