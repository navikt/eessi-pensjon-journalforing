package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EnhetHandlerTest {

    @Test
    fun `Gyldig automatisk journalføring`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { saktype } returns ALDER
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
            every { saktype } returns ALDER
            every { aktorId } returns "1111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "HendelseType.MOTTATT skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Manglende saktype skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { saktype } returns null
            every { aktorId } returns "1111"
            every { sakInformasjon?.sakId } returns "555"
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med saktype == null skal aldri automatisk journalføres"
        )
    }

    @Test
    fun `Manglende aktorId skal manuelt journalføres`() {
        val req = mockk<OppgaveRoutingRequest> {
            every { hendelseType} returns HendelseType.SENDT
            every { saktype } returns ALDER
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
            every { saktype } returns ALDER
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
            every { saktype } returns ALDER
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
            every { saktype } returns ALDER
            every { aktorId } returns "111"
            every { sakInformasjon?.sakId } returns " "
        }

        assertFalse(
                MockBuc().kanAutomatiskJournalfores(req),
                "Request med tom sakId skal aldri automatisk journalføres"
        )
    }

    private class MockBuc : EnhetHandler {
        override fun finnEnhet(request: OppgaveRoutingRequest): Enhet = mockk()
    }
}
