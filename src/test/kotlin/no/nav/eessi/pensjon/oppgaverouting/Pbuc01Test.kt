package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Pbuc01Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_01) as Pbuc01

    @Test
    fun `Inneholder diskresjonskode`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true)

        // SPSF er strengt fortrolig og skal returnere Enhet.DISKRESJONSKODE (vikafossen)
        every { request.diskresjonskode } returns Diskresjonskode.SPSF
        assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))

        // SPSF er mindre fortrolig og følger vanlig saksflyt
        every { request.diskresjonskode } returns Diskresjonskode.SPFO
        assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))
    }

    @Test
    fun `Automatisk journalføring`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.SENDT
            every { diskresjonskode } returns null
            every { ytelseType } returns YtelseType.GENRL
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @Test
    fun `Manuell behandling, bosatt norge`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.MOTTATT
            every { diskresjonskode } returns null
            every { ytelseType } returns null
            every { aktorId } returns null
            every { sakInformasjon } returns null
            every { bosatt } returns Bosatt.NORGE
        }

        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))
    }

    @Test
    fun `Manuell behandling, bosatt utland`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.MOTTATT
            every { diskresjonskode } returns null
            every { ytelseType } returns null
            every { aktorId } returns null
            every { sakInformasjon } returns null
            every { bosatt } returns Bosatt.UTLAND
        }

        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))
    }
}
