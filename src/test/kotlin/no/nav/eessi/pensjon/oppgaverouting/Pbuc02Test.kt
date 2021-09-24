package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Pbuc02Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_02) as Pbuc02

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
        @EnumSource(Saktype::class)
        fun `Sendt hendelse kan automatisk journalføres`(type: Saktype) {
            // Gyldig sak hvor sakStatus IKKE er AVSLUTTET skal alltid automatisk journalføres
            val requestNorge = SENDT.request(type, "NOR", SakStatus.TIL_BEHANDLING)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(requestNorge))

            val requestUtland = SENDT.request(type, "SWE", SakStatus.TIL_BEHANDLING)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(requestUtland))
        }

        @Test
        fun `Sendt hendelse med sakType UFOREP og sakStatus AVSLUTTET`() {
            val requestNorge = SENDT.request(Saktype.UFOREP, "NOR", SakStatus.AVSLUTTET)

            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(requestNorge),
                    "Skal aldri automatisk journalføres dersom saktype == UFOREP og SakStatus == AVSLUTTET"
            )

            val requestUtland = SENDT.request(Saktype.UFOREP, "SWE", SakStatus.AVSLUTTET)

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
                    handler.hentEnhet(SENDT.request(Saktype.UFOREP, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(Saktype.UFOREP, "NOR", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(Saktype.ALDER, "NOR"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(Saktype.BARNEP, "NOR"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(Saktype.GJENLEV, "NOR"))
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
                    handler.hentEnhet(SENDT.request(Saktype.UFOREP, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(Saktype.UFOREP, "SWE", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(Saktype.ALDER, "SWE"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(Saktype.BARNEP, "SWE"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(Saktype.GJENLEV, "SWE"))
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
        @EnumSource(Saktype::class)
        fun `Mottatt hendelse skal aldri automatisk journalføres, bosatt NORGE`(type: Saktype) {
            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(MOTTATT.request(type, "NOR"))
            )
        }

        @ParameterizedTest
        @EnumSource(Saktype::class)
        fun `Mottatt hendelse skal aldri automatisk journalføres, bosatt UTLAND`(type: Saktype) {
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
                    handler.hentEnhet(MOTTATT.request(Saktype.UFOREP, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(Saktype.UFOREP, "NOR", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.NFP_UTLAND_AALESUND,
                    handler.hentEnhet(MOTTATT.request(Saktype.ALDER, "NOR"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(Saktype.BARNEP, "NOR"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(Saktype.GJENLEV, "NOR"))
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
                    handler.hentEnhet(MOTTATT.request(Saktype.UFOREP, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(Saktype.UFOREP, "SWE", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(Saktype.ALDER, "SWE"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(Saktype.BARNEP, "SWE"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(Saktype.GJENLEV, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(null, "SWE"))
            )
        }
    }

    private fun HendelseType.request(
        type: Saktype?,
        landkode: String,
        status: SakStatus = SakStatus.TIL_BEHANDLING
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
            every { bucType } returns BucType.P_BUC_02
        }
    }
}
