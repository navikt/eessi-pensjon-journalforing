package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_05
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.shared.person.FodselsnummerGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class Pbuc05Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(P_BUC_05) as Pbuc05

    companion object {
        private const val FNR_63 = "05115743432"        // SLAPP DORULL 62
        private const val FNR_54 = "11067122781"    // KRAFTIG VEGGPRYD
        private const val FNR_BARN = "12011577847"      // STERK BUSK
        private val FNR_61 = FodselsnummerGenerator.generateFnrForTest(61)  //"12105928066"        // STERK UTEDO
    }

    @Test
    fun `Bosatt norge`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.NORGE
            every { sedType } returns null
            every { bucType } returns P_BUC_05
        }


        val fnr_61 = FodselsnummerGenerator.generateFnrForTest(61)

        every { request.fdato } returns Fodselsnummer.fra(fnr_61)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_63)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))
    }

    @Test
    fun `Bosatt norge avsenderland Tyskland uføre mellom 18 til 60 år`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.NORGE
            every { avsenderLand } returns "DE"
            every { sedType } returns null
            every { bucType } returns P_BUC_05
        }

        every { request.fdato } returns Fodselsnummer.fra(FNR_61)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_54)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

    }


    @Test
    fun `Bosatt utland avsenderland Tyskland uføre mellom 18 til 60 år`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.UTLAND
            every { avsenderLand } returns "DE"
            every { sedType } returns null
            every { bucType } returns P_BUC_05
        }

        every { request.fdato } returns Fodselsnummer.fra(FNR_63)!!.getBirthDate()
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_54)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLAND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))

    }

    @Test
    fun `Bosatt norge avsenderland Danmark uføre mellom 18 til 62 år`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { bosatt } returns Bosatt.NORGE
            every { avsenderLand } returns "DK"
            every { sedType } returns null
            every { bucType } returns P_BUC_05
        }

        every { request.fdato } returns Fodselsnummer.fra(FNR_63)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_BARN)!!.getBirthDate()
        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))

        every { request.fdato } returns Fodselsnummer.fra(FNR_54)!!.getBirthDate()
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, handler.hentEnhet(request))
    }

}
