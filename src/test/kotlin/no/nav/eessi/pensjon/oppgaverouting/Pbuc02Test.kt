package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class Pbuc02Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(P_BUC_02) as Pbuc02

    /**
     * Test av HendelseType.SENDT
     */
    @Nested
    inner class Sendt {
        @Test
        fun `Sendt hendelse med diskresjonskode`() {
            val request = mockk<OppgaveRoutingRequest>(relaxed = true)

            // SPSF er strengt fortrolig og skal returnere Enhet.DISKRESJONSKODE (vikafossen)
            every { request.harAdressebeskyttelse } returns true
            assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))

            // SPSF er mindre fortrolig og følger vanlig saksflyt
            every { request.harAdressebeskyttelse } returns false
            assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))
        }

        @ParameterizedTest
        @EnumSource(SakType::class)
        fun `Sendt hendelse kan automatisk journalføres`(type: SakType) {
            // Gyldig sak hvor sakStatus IKKE er AVSLUTTET skal alltid automatisk journalføres
            val requestNorge = SENDT.request(type, "NOR", TIL_BEHANDLING)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(requestNorge))

            val requestUtland = SENDT.request(type, "SWE", TIL_BEHANDLING)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(requestUtland))
        }

        @Test
        fun `Sendt hendelse med sakType UFOREP og sakStatus AVSLUTTET`() {
            val requestNorge = SENDT.request(UFOREP, "NOR", AVSLUTTET)

            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(requestNorge),
                    "Skal aldri automatisk journalføres dersom saktype == UFOREP og SakStatus == AVSLUTTET"
            )

            val requestUtland = SENDT.request(UFOREP, "SWE", AVSLUTTET)

            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(requestUtland),
                    "Skal aldri automatisk journalføres dersom saktype == UFOREP og SakStatus == AVSLUTTET"
            )
        }

        @Test
        fun `Manglende saktype går til ID_OG_FORDELING`() {
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(type = null, landkode = "NOR"))
            )

            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(type = null, landkode = "SWE"))
            )
        }

        @Test
        fun `Sendt hendelse som er gyldig, bosatt NORGE`() {
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(UFOREP, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(UFOREP, "NOR", AVSLUTTET))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(ALDER, "NOR"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(BARNEP, "NOR"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(GJENLEV, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(null, "NOR"))
            )
        }

        @Test
        fun `Sendt hendelse som er gyldig, bosatt UTLAND`() {
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(UFOREP, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(UFOREP, "SWE", AVSLUTTET))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(ALDER, "SWE"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(BARNEP, "SWE"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(GJENLEV, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(null, "SWE"))
            )
        }
    }

    /**
     * Test av HendelseType.MOTTATT
     */
    @Nested
    inner class Mottatt {
        @Test
        fun `Mottatt hendlse med diskresjonskode`() {
            val request = mockk<OppgaveRoutingRequest>(relaxed = true)

            // SPSF er strengt fortrolig og skal returnere Enhet.DISKRESJONSKODE (vikafossen)
            every { request.harAdressebeskyttelse } returns true
            assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))

            // SPSF er mindre fortrolig og følger vanlig saksflyt
            every { request.harAdressebeskyttelse } returns false
            assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(request))
        }

        @ParameterizedTest
        @EnumSource(SakType::class)
        fun `Mottatt hendelse skal aldri automatisk journalføres, bosatt NORGE`(type: SakType) {
            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(MOTTATT.request(type, "NOR"))
            )
        }

        @ParameterizedTest
        @EnumSource(SakType::class)
        fun `Mottatt hendelse skal aldri automatisk journalføres, bosatt UTLAND`(type: SakType) {
            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(MOTTATT.request(type, "SWE"))
            )
        }

        @Test
        fun `Manglende saktype går til ID_OG_FORDELING`() {
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(type = null, landkode = "NOR"))
            )
        }

        @Test
        fun `Mottatt hendelse som er gyldig, bosatt NORGE`() {
            assertEquals(
                    Enhet.UFORE_UTLANDSTILSNITT,
                    handler.hentEnhet(MOTTATT.request(UFOREP, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(UFOREP, "NOR", AVSLUTTET))
            )
            assertEquals(
                    Enhet.NFP_UTLAND_AALESUND,
                    handler.hentEnhet(MOTTATT.request(ALDER, "NOR"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(BARNEP, "NOR"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(GJENLEV, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(null, "NOR"))
            )
        }

        @Test
        fun `Mottatt hendelse som er gyldig, bosatt UTLAND`() {
            assertEquals(
                    Enhet.UFORE_UTLAND,
                    handler.hentEnhet(MOTTATT.request(UFOREP, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(UFOREP, "SWE", AVSLUTTET))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(ALDER, "SWE"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(BARNEP, "SWE"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(GJENLEV, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(null, "SWE"))
            )
        }
    }

    private fun HendelseType.request(
        type: SakType?,
        landkode: String,
        status: SakStatus = TIL_BEHANDLING
    ): OppgaveRoutingRequest {
        val hendelse = this

        return mockk {
            every { aktorId } returns "12345"
            every { hendelseType } returns hendelse
            every { saktype } returns type
            every { sakInformasjon?.sakId } returns "sakId"
            every { sakInformasjon?.sakType } returns type
            every { sakInformasjon?.sakStatus } returns status
            every { bosatt } returns Bosatt.fraLandkode(landkode)
            every { harAdressebeskyttelse } returns false
            every { sedType } returns null
            every { bucType } returns P_BUC_02
        }
    }
}
