package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Pbuc04Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_04) as Pbuc04

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

    @ParameterizedTest
    @EnumSource(YtelseType::class)
    fun `Automatisk journalføring, uavhengig av ytelsetype`(type: YtelseType) {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.SENDT
            every { diskresjonskode } returns null
            every { ytelseType } returns type
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(YtelseType::class)
    fun `Mottatt hendelse skal aldri journalføres automatisk`(type: YtelseType) {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns HendelseType.MOTTATT
            every { diskresjonskode } returns null
            every { ytelseType } returns type
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns "555"
            every { bosatt } returns Bosatt.NORGE
        }

        assertNotEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Manuell behandling, bosatt norge`(hendelse: HendelseType) {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns hendelse
            every { diskresjonskode } returns null
            every { ytelseType } returns null
            every { aktorId } returns null
            every { sakInformasjon } returns null
            every { bosatt } returns Bosatt.NORGE
        }

        assertEquals(Enhet.NFP_UTLAND_AALESUND, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Manuell behandling, bosatt utland`(hendelse: HendelseType) {
        val request = mockk<OppgaveRoutingRequest> {
            every { hendelseType } returns hendelse
            every { diskresjonskode } returns null
            every { ytelseType } returns null
            every { aktorId } returns null
            every { sakInformasjon } returns null
            every { bosatt } returns Bosatt.UTLAND
        }

        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(request))
    }
}
