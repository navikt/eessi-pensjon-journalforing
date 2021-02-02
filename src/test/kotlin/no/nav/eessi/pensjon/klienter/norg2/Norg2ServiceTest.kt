package no.nav.eessi.pensjon.klienter.norg2

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Enhet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class Norg2ServiceTest {

    private val norg2Klient = mockk<Norg2Klient>(relaxed = true)

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

    @Test
    fun `Opprett Norg2 Request, person bosatt i norge, ingen adressebeskyttelse`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest(landkode = "NOR"))

        val expectedRequest = Norg2ArbeidsfordelingRequest(behandlingstype = BehandlingType.BOSATT_NORGE.kode)

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    @Test
    fun `Opprett Norg2 Request, person bosatt i utland, ingen adressebeskyttelse`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest(landkode = "SWE"))

        val expectedRequest = Norg2ArbeidsfordelingRequest(behandlingstype = BehandlingType.BOSATT_UTLAND.kode)

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    @Test
    fun `Opprett Norg2 Request, person bosatt i utland, har adressebeskyttelse`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest(true, "SWE"))

        val expectedRequest = Norg2ArbeidsfordelingRequest(
            diskresjonskode = "SPSF",
            tema = "ANY")

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    @Test
    fun `Opprett Norg2 Request, person bosatt i norge, har adressebeskyttelse`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest(true, "NOR"))

        val expectedRequest = Norg2ArbeidsfordelingRequest(
            diskresjonskode = "SPSF",
            tema = "ANY")

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    @Test
    fun `Opprett Norg2 Request, person bosatt i utland, har geografisk tilknytning`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest(false, "SWE", "1234"))

        val expectedRequest = Norg2ArbeidsfordelingRequest(
            behandlingstype = BehandlingType.BOSATT_UTLAND.kode,
            geografiskOmraade = "1234"
        )

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    @Test
    fun `Opprett Norg2 Request, person bosatt i norge, har geografisk tilknytning`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest(false, "NOR", "1234"))

        val expectedRequest = Norg2ArbeidsfordelingRequest(
            behandlingstype = BehandlingType.BOSATT_NORGE.kode,
            geografiskOmraade = "1234"
        )

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    @Test
    fun `Opprett Norg2 Request, mangler alt`() {
        service.hentArbeidsfordelingEnhet(NorgKlientRequest())

        val expectedRequest = Norg2ArbeidsfordelingRequest(behandlingstype = BehandlingType.BOSATT_UTLAND.kode)

        verify(exactly = 1) { norg2Klient.hentArbeidsfordelingEnheter(expectedRequest) }
    }

    private fun getJsonFileFromResource(filename: String): List<Norg2ArbeidsfordelingItem> {
        val json = javaClass.getResource(filename).readText()

        return mapJsonToAny(json, typeRefs())
    }

}
