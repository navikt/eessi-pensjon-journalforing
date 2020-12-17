package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Pbuc10Test {

    companion object {
        private val DUMMY_FNR = Fodselsnummer.fra("09035225916") // Testbruker SLAPP SKILPADDE
    }

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_10) as Pbuc10

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
    fun `Sak er ugyldig`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { identifisertPerson } returns IdentifisertPerson("1231", "ole dunk", null, "NOR", "1234", PersonRelasjon(DUMMY_FNR, Relasjon.GJENLEVENDE, YtelseType.GJENLEV, SedType.P15000))
            every { diskresjonskode } returns null
            every { ytelseType } returns YtelseType.UFOREP
            every { hendelseType } returns SENDT
            every { sakInformasjon?.sakStatus } returns SakStatus.AVSLUTTET
            every { sakInformasjon?.sakType } returns YtelseType.UFOREP
        }

        assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
    }

    @Test
    fun `Kan automatisk journalføres`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { hendelseType } returns SENDT
            every { ytelseType } returns YtelseType.UFOREP
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @Test
    fun `Mottatt sak til manuell behandling, bosatt norge`() {
        assertEquals(
                Enhet.NFP_UTLAND_AALESUND,
                handler.hentEnhet(manuellRequest(MOTTATT, YtelseType.ALDER, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.NFP_UTLAND_AALESUND,
                handler.hentEnhet(manuellRequest(MOTTATT, YtelseType.GJENLEV, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.UFORE_UTLANDSTILSNITT,
                handler.hentEnhet(manuellRequest(MOTTATT, YtelseType.UFOREP, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(MOTTATT, YtelseType.OMSORG, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(MOTTATT, YtelseType.GENRL, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(MOTTATT, YtelseType.BARNEP, Bosatt.NORGE))
        )
    }

    @Test
    fun `Sendt sak til manuell behandling, bosatt norge`() {
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, YtelseType.ALDER, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, YtelseType.GJENLEV, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.UFORE_UTLANDSTILSNITT,
                handler.hentEnhet(manuellRequest(SENDT, YtelseType.UFOREP, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, YtelseType.OMSORG, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, YtelseType.GENRL, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, YtelseType.BARNEP, Bosatt.NORGE))
        )
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Mottatt sak til manuell behandling, bosatt utland`(hendelse: HendelseType) {
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, YtelseType.ALDER, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, YtelseType.GJENLEV, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.UFORE_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, YtelseType.UFOREP, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, YtelseType.OMSORG, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, YtelseType.GENRL, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, YtelseType.BARNEP, Bosatt.UTLAND))
        )
    }

    private fun manuellRequest(
            hendelse: HendelseType,
            ytelse: YtelseType?,
            land: Bosatt
    ): OppgaveRoutingRequest {
        return mockk {
            if (hendelse == SENDT)
                every { identifisertPerson } returns IdentifisertPerson("1231", "ole dunk", null, "NOR", "1234", PersonRelasjon(DUMMY_FNR, Relasjon.GJENLEVENDE, YtelseType.GJENLEV, SedType.P15000))

            every { diskresjonskode } returns null
            every { hendelseType } returns hendelse
            every { ytelseType } returns ytelse
            every { aktorId } returns "111"
            every { landkode } returns "NOR"
            every { bosatt } returns land
            every { sakInformasjon } returns null
        }
    }
}
