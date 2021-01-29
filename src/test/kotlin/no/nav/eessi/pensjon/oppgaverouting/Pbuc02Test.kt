package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType
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
        @EnumSource(YtelseType::class)
        fun `Sendt hendelse kan automatisk journalføres`(type: YtelseType) {
            // Gyldig sak hvor sakStatus IKKE er AVSLUTTET skal alltid automatisk journalføres
            val requestNorge = SENDT.request(type, "NOR", SakStatus.TIL_BEHANDLING)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(requestNorge))

            val requestUtland = SENDT.request(type, "SWE", SakStatus.TIL_BEHANDLING)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(requestUtland))
        }

        @Test
        fun `Sendt hendelse med sakType UFOREP og sakStatus AVSLUTTET`() {
            val requestNorge = SENDT.request(YtelseType.UFOREP, "NOR", SakStatus.AVSLUTTET)

            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(requestNorge),
                    "Skal aldri automatisk journalføres dersom YtelseType == UFOREP og SakStatus == AVSLUTTET"
            )

            val requestUtland = SENDT.request(YtelseType.UFOREP, "SWE", SakStatus.AVSLUTTET)

            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(requestUtland),
                    "Skal aldri automatisk journalføres dersom YtelseType == UFOREP og SakStatus == AVSLUTTET"
            )
        }

        @Test
        fun `Manglende ytelseType går til ID_OG_FORDELING`() {
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
                    handler.hentEnhet(SENDT.request(YtelseType.UFOREP, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(YtelseType.UFOREP, "NOR", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(YtelseType.ALDER, "NOR"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(YtelseType.BARNEP, "NOR"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(YtelseType.GJENLEV, "NOR"))
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
                    handler.hentEnhet(SENDT.request(YtelseType.UFOREP, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(SENDT.request(YtelseType.UFOREP, "SWE", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(YtelseType.ALDER, "SWE"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(YtelseType.BARNEP, "SWE"))
            )
            assertEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(SENDT.request(YtelseType.GJENLEV, "SWE"))
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
        @EnumSource(YtelseType::class)
        fun `Mottatt hendelse skal aldri automatisk journalføres, bosatt NORGE`(type: YtelseType) {
            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(MOTTATT.request(type, "NOR"))
            )
        }

        @ParameterizedTest
        @EnumSource(YtelseType::class)
        fun `Mottatt hendelse skal aldri automatisk journalføres, bosatt UTLAND`(type: YtelseType) {
            assertNotEquals(
                    Enhet.AUTOMATISK_JOURNALFORING,
                    handler.hentEnhet(MOTTATT.request(type, "SWE"))
            )
        }

        @Test
        fun `Manglende ytelseType går til ID_OG_FORDELING`() {
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(type = null, landkode = "NOR"))
            )
        }

        @Test
        fun `Mottatt hendelse som er gyldig, bosatt NORGE`() {
            assertEquals(
                    Enhet.UFORE_UTLANDSTILSNITT,
                    handler.hentEnhet(MOTTATT.request(YtelseType.UFOREP, "NOR"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(YtelseType.UFOREP, "NOR", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.NFP_UTLAND_AALESUND,
                    handler.hentEnhet(MOTTATT.request(YtelseType.ALDER, "NOR"))
            )
            assertEquals(
                    Enhet.NFP_UTLAND_AALESUND,
                    handler.hentEnhet(MOTTATT.request(YtelseType.BARNEP, "NOR"))
            )
            assertEquals(
                    Enhet.NFP_UTLAND_AALESUND,
                    handler.hentEnhet(MOTTATT.request(YtelseType.GJENLEV, "NOR"))
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
                    handler.hentEnhet(MOTTATT.request(YtelseType.UFOREP, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(YtelseType.UFOREP, "SWE", SakStatus.AVSLUTTET))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(YtelseType.ALDER, "SWE"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(YtelseType.BARNEP, "SWE"))
            )
            assertEquals(
                    Enhet.PENSJON_UTLAND,
                    handler.hentEnhet(MOTTATT.request(YtelseType.GJENLEV, "SWE"))
            )
            assertEquals(
                    Enhet.ID_OG_FORDELING,
                    handler.hentEnhet(MOTTATT.request(null, "SWE"))
            )
        }
    }

    private fun HendelseType.request(
            type: YtelseType?,
            landkode: String,
            status: SakStatus = SakStatus.TIL_BEHANDLING
    ): OppgaveRoutingRequest {
        val hendelse = this

        return mockk {
            every { aktorId } returns "12345"
            every { hendelseType } returns hendelse
            every { ytelseType } returns type
            every { sakInformasjon?.sakId } returns "sakId"
            every { sakInformasjon?.sakType } returns type
            every { sakInformasjon?.sakStatus } returns status
            every { bosatt } returns Bosatt.fraLandkode(landkode)
            every { harAdressebeskyttelse } returns false
        }
    }
}
