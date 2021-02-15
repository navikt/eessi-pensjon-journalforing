package no.nav.eessi.pensjon.handler

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.eux.model.sed.SedType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class OppgaveMeldingSerdeTest {

    @Test
    fun serde_journalforing() {
        val melding = OppgaveMelding(
                SedType.P8000,
                "12345",
                Enhet.ID_OG_FORDELING,
                "aktoerId",
                "TEST",
                HendelseType.SENDT,
                null
        )

        val serialized = melding.toJson()

        val deserialized = mapJsonToAny(serialized, typeRefs<OppgaveMelding>())

        assertEquals(melding.sedType, deserialized.sedType)
        assertEquals(melding.journalpostId, deserialized.journalpostId)
        assertEquals(melding.tildeltEnhetsnr, deserialized.tildeltEnhetsnr)
        assertEquals(melding.aktoerId, deserialized.aktoerId)
        assertEquals(melding.rinaSakId, deserialized.rinaSakId)
        assertEquals(melding.hendelseType, deserialized.hendelseType)
        assertEquals(melding.filnavn, deserialized.filnavn)
        assertEquals("JOURNALFORING", deserialized.oppgaveType())
    }

    @Test
    fun serde_behandleSed() {
        val melding = OppgaveMelding(
                SedType.P8000,
                null,
                Enhet.ID_OG_FORDELING,
                "aktoerId",
                "TEST",
                HendelseType.SENDT,
                "filnavn"
        )

        val serialized = melding.toJson()

        val deserialized = mapJsonToAny(serialized, typeRefs<OppgaveMelding>())

        assertEquals(melding.sedType, deserialized.sedType)
        assertEquals(melding.journalpostId, deserialized.journalpostId)
        assertEquals(melding.tildeltEnhetsnr, deserialized.tildeltEnhetsnr)
        assertEquals(melding.aktoerId, deserialized.aktoerId)
        assertEquals(melding.rinaSakId, deserialized.rinaSakId)
        assertEquals(melding.hendelseType, deserialized.hendelseType)
        assertEquals(melding.filnavn, deserialized.filnavn)
        assertEquals("BEHANDLE_SED", deserialized.oppgaveType())
    }
}