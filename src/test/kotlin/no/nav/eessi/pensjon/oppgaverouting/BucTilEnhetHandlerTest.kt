package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BucTilEnhetHandlerTest {

    @Test
    fun `Gyldig automatisk journalføring`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { ytelseType } returns YtelseType.ALDER
            every { aktorId } returns "1111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertTrue(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med gyldige verdier skal gå til automatisk"
        )
    }

    @Test
    fun `Mottatt hendelse skal ikke få automatisk`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.MOTTATT
            every { ytelseType } returns YtelseType.ALDER
            every { aktorId } returns "1111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "HendelseType.MOTTATT skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Manglende ytelseType skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { ytelseType } returns null
            every { aktorId } returns "1111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med ytelseType == null skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Manglende aktorId skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { ytelseType } returns YtelseType.ALDER
            every { aktorId } returns null
            every { sakInformasjon?.sakId } returns "555"
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med aktorId == null skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Tom aktorId skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { ytelseType } returns YtelseType.ALDER
            every { aktorId } returns " "
            every { sakInformasjon?.sakId } returns "555"
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med tom aktorId skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Manglende sakId skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { ytelseType } returns YtelseType.ALDER
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns null
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med sakId == null skal aldri automatisk journalføres"
        )    }

    @Test
    fun `Tom sakId skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { ytelseType } returns YtelseType.ALDER
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns " "
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med tom sakId skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Diskresjonskode SPSF er strengt fortrolig`() {
        assertTrue(
                MockBuc().erStrengtFortrolig(Diskresjonskode.SPSF),
                "Diskresjonkode == SPSF skal gi true, ettersom dette er strengt fortrolig kode"
        )
    }

    @Test
    fun `Diskresjonskode SPFO skal ikke være fortrolig`() {
        assertFalse(
                MockBuc().erStrengtFortrolig(Diskresjonskode.SPFO),
                "Diskresjonkode == SPFO skal gi false"
        )
    }

    @Test
    fun `Diskresjonskode lik null skal ikke være fortrolig`() {
        assertFalse(
                MockBuc().erStrengtFortrolig(null),
                "Diskresjonkode == null skal gi false"
        )
    }

    private class MockBuc : BucTilEnhetHandler {
        override fun hentEnhet(request: OppgaveRoutingRequest): Enhet = mockk()
    }
}
