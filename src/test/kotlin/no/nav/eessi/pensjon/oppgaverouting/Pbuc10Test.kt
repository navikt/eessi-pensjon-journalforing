package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class Pbuc10Test {

    companion object {
        private val DUMMY_FNR = Fodselsnummer.fra("09035225916") // Testbruker SLAPP SKILPADDE
    }

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_10) as Pbuc10

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

    @Test
    fun `Sak er ugyldig`() {
        val request = mockk<OppgaveRoutingRequest> {
            every { identifisertPerson } returns IdentifisertPerson(
                "1231", "ole dunk", "NOR", "1234", SEDPersonRelasjon(
                    DUMMY_FNR, Relasjon.GJENLEVENDE, Saktype.GJENLEV, SedType.P15000, rinaDocumentId =  "3123123"
                )
            )
            every { harAdressebeskyttelse } returns false
            every { saktype } returns Saktype.UFOREP
            every { hendelseType } returns SENDT
            every { sakInformasjon?.sakStatus } returns SakStatus.AVSLUTTET
            every { sakInformasjon?.sakType } returns Saktype.UFOREP
            every { sedType } returns SedType.P15000
            every { bucType } returns BucType.P_BUC_10

        }

        assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
    }

    @Test
    fun `Kan automatisk journalføres`() {
        val request = mockk<OppgaveRoutingRequest>(relaxed = true) {
            every { hendelseType } returns SENDT
            every { saktype } returns Saktype.UFOREP
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @Test
    fun `Mottatt sak til manuell behandling, bosatt norge`() {
        assertEquals(
                Enhet.NFP_UTLAND_AALESUND,
                handler.hentEnhet(manuellRequest(MOTTATT, Saktype.ALDER, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.NFP_UTLAND_AALESUND,
                handler.hentEnhet(manuellRequest(MOTTATT, Saktype.GJENLEV, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.UFORE_UTLANDSTILSNITT,
                handler.hentEnhet(manuellRequest(MOTTATT, Saktype.UFOREP, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(MOTTATT, Saktype.OMSORG, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(MOTTATT, Saktype.GENRL, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(MOTTATT, Saktype.BARNEP, Bosatt.NORGE))
        )
    }

    @Test
    fun `Sendt sak til manuell behandling, bosatt norge`() {
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, Saktype.ALDER, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, Saktype.GJENLEV, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.UFORE_UTLANDSTILSNITT,
                handler.hentEnhet(manuellRequest(SENDT, Saktype.UFOREP, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, Saktype.OMSORG, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, Saktype.GENRL, Bosatt.NORGE))
        )
        assertEquals(
                Enhet.ID_OG_FORDELING,
                handler.hentEnhet(manuellRequest(SENDT, Saktype.BARNEP, Bosatt.NORGE))
        )
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Mottatt sak til manuell behandling, bosatt utland`(hendelse: HendelseType) {
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, Saktype.ALDER, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, Saktype.GJENLEV, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.UFORE_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, Saktype.UFOREP, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, Saktype.OMSORG, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, Saktype.GENRL, Bosatt.UTLAND))
        )
        assertEquals(
                Enhet.PENSJON_UTLAND,
                handler.hentEnhet(manuellRequest(hendelse, Saktype.BARNEP, Bosatt.UTLAND))
        )
    }

    private fun manuellRequest(
        hendelse: HendelseType,
        ytelse: Saktype?,
        land: Bosatt
    ): OppgaveRoutingRequest {
        return mockk {
            if (hendelse == SENDT)
                every { identifisertPerson } returns IdentifisertPerson(
                    "1231", "ole dunk", "NOR", "1234", SEDPersonRelasjon(
                        DUMMY_FNR, Relasjon.GJENLEVENDE, Saktype.GJENLEV, SedType.P15000, rinaDocumentId =  "3123123"
                    )
                )

            every { harAdressebeskyttelse } returns false
            every { hendelseType } returns hendelse
            every { saktype } returns ytelse
            every { aktorId } returns "111"
            every { landkode } returns "NOR"
            every { bosatt } returns land
            every { sakInformasjon } returns null
            every { bucType } returns BucType.P_BUC_10
            every { sedType } returns SedType.P15000


        }
    }
}
