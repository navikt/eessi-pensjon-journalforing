package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class Pbuc01Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(P_BUC_01) as Pbuc01

    @Test
    fun `Inneholder diskresjonskode`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true)

        // SPSF er strengt fortrolig og skal returnere Enhet.DISKRESJONSKODE (vikafossen)
        every { request.harAdressebeskyttelse } returns true
        assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))

        // SPSF er mindre fortrolig og følger vanlig saksflyt
        every { request.harAdressebeskyttelse } returns false
        assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(Saktype::class)
    fun `Automatisk journalføring, uavhengig av saktype`(type: Saktype) {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.SENDT
            every { harAdressebeskyttelse } returns false
            every { saktype } returns type
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns "555"
            every { sedType } returns null
            every { bucType } returns P_BUC_01
        }

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @Test
    fun `Manuell behandling, bosatt norge`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.MOTTATT
            every { harAdressebeskyttelse } returns false
            every { saktype } returns null
            every { aktorId } returns null
            every { sakInformasjon } returns null
            every { bosatt } returns Bosatt.NORGE
            every { sedType } returns null
            every { bucType } returns P_BUC_01
        }

        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))
    }

    @Test
    fun `Manuell behandling, bosatt utland`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.MOTTATT
            every { harAdressebeskyttelse } returns false
            every { saktype } returns null
            every { aktorId } returns null
            every { sakInformasjon } returns null
            every { bosatt } returns Bosatt.UTLAND
            every { sedType } returns null
            every { bucType } returns P_BUC_01
        }

        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))
    }
}
