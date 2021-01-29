package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Pbuc06Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_06) as DefaultBucTilEnhetHandler

    companion object {
        private const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        private const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        private const val FNR_BARN = "12011577847"      // STERK BUSK
    }

    @Test
    fun `Sjekk diskresjonskode blir behandlet korrekt`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true)

        // SPSF er strengt fortrolig og skal returnere Enhet.DISKRESJONSKODE (vikafossen)
        every { request.harAdressebeskyttelse } returns true
        assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))

        // SPSF er mindre fortrolig og følger vanlig saksflyt
        every { request.harAdressebeskyttelse } returns false
        assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))
    }

    @Test
    fun `Bosatt norge`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.NORGE
        }

        every { request.fdato } returns Fodselsnummer.fra(FNR_OVER_60)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_VOKSEN)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, handler.hentEnhet(request))
    }

    @Test
    fun `Bosatt utland`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.UTLAND
        }

        every { request.fdato } returns Fodselsnummer.fra(FNR_OVER_60)!!.getBirthDate()
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_VOKSEN)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLAND, handler.hentEnhet(request))
    }
}
