package no.nav.eessi.pensjon.handler


import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OppgaveMeldingSerdeTest {

    @Test
    fun serde_journalforing() {
        val melding = OppgaveMelding(
            P8000,
            "12345",
            Enhet.ID_OG_FORDELING,
            "aktoerId",
            "TEST",
            SENDT,
            null,
            OppgaveType.JOURNALFORING
        )

        val serialized = melding.toJson()

        val deserialized = mapJsonToAny<OppgaveMelding>(serialized)

        assertEquals(melding.sedType, deserialized.sedType)
        assertEquals(melding.journalpostId, deserialized.journalpostId)
        assertEquals(melding.tildeltEnhetsnr, deserialized.tildeltEnhetsnr)
        assertEquals(melding.aktoerId, deserialized.aktoerId)
        assertEquals(melding.rinaSakId, deserialized.rinaSakId)
        assertEquals(melding.hendelseType, deserialized.hendelseType)
        assertEquals(melding.filnavn, deserialized.filnavn)
        assertEquals(OppgaveType.JOURNALFORING, melding.oppgaveType)
    }

    @Test
    fun serde_behandleSed() {
        val melding = OppgaveMelding(
            P8000,
            null,
            Enhet.ID_OG_FORDELING,
            "aktoerId",
            "TEST",
            SENDT,
            "filnavn",
            OppgaveType.BEHANDLE_SED
        )

        val serialized = melding.toJson()

        val deserialized = mapJsonToAny<OppgaveMelding>(serialized)

        assertEquals(melding.sedType, deserialized.sedType)
        assertEquals(melding.journalpostId, deserialized.journalpostId)
        assertEquals(melding.tildeltEnhetsnr, deserialized.tildeltEnhetsnr)
        assertEquals(melding.aktoerId, deserialized.aktoerId)
        assertEquals(melding.rinaSakId, deserialized.rinaSakId)
        assertEquals(melding.hendelseType, deserialized.hendelseType)
        assertEquals(melding.filnavn, deserialized.filnavn)
        assertEquals(OppgaveType.BEHANDLE_SED, melding.oppgaveType)
    }
}