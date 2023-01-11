package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class DefaultBucTilEnhetHandlerTest {

    companion object {
        private const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        private const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        private const val FNR_BARN = "12011577847"      // STERK BUSK
    }

    @ParameterizedTest
    @EnumSource(value = BucType::class, names = ["P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09"])
    fun `Sjekk diskresjonskode blir behandlet korrekt`(bucType: BucType) {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true)

        val handler = BucTilEnhetHandlerCreator.getHandler(bucType)

        // SPSF er strengt fortrolig og skal returnere Enhet.DISKRESJONSKODE (vikafossen)
        every { request.harAdressebeskyttelse } returns true
        assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))

        // SPSF er mindre fortrolig og følger vanlig saksflyt
        every { request.harAdressebeskyttelse } returns false
        assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(value = BucType::class, names = ["P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09"])
    fun `Bosatt norge`(bucType: BucType) {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.NORGE
        }

        val handler = BucTilEnhetHandlerCreator.getHandler(bucType)

        every { request.fdato } returns Fodselsnummer.fra(FNR_OVER_60)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_VOKSEN)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(value = BucType::class, names = ["P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09"])
    fun `Bosatt utland`(bucType: BucType) {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.UTLAND
        }

        val handler = BucTilEnhetHandlerCreator.getHandler(bucType)

        every { request.fdato } returns Fodselsnummer.fra(FNR_OVER_60)!!.getBirthDate()
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_VOKSEN)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLAND, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(value = BucType::class, names = ["P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09"])
    fun `Bosatt norge uføre mellom 18 til 62 år`(bucType: BucType) {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.NORGE
        }

        val handler = BucTilEnhetHandlerCreator.getHandler(bucType)

        every { request.fdato } returns Fodselsnummer.fra(FNR_OVER_60)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_VOKSEN)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, handler.hentEnhet(request))
    }


}
