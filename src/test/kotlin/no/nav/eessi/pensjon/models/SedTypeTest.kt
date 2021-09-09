package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.SedTypeUtils.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.models.sed.SedTypeUtils.ugyldigeTyper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class SedTypeTest {

    @ParameterizedTest
    @EnumSource(
        SedType::class, names = [
        "P13000", "X001", "X002", "X003", "X004", "X006", "X007",
        "X009", "X011", "X012", "X013", "X050", "X100",
        "H001", "H002", "H020", "H021", "H120", "H121", "R004", "R006"
    ])
    fun `Verifiser ugyldige SED-typer`(type: SedType) {
        assertTrue(
            type in ugyldigeTyper,
            "SedType.${type.name} mangler i listen over ugyldige typer"
        )
    }

    @Test
    fun `Sjekk antall SED-typer`() {
        assertEquals(
            76,
            SedType.values().size,
            "Antall SED-typer har blitt endret."
        )
    }

    @Test
    fun `Sjekk antall ugyldige SED-typer`() {
        assertEquals(
            21,
            ugyldigeTyper.size,
            "Antall ugyldige SED-typer har blitt endret."
        )
    }

    @Test
    fun `Sjekk antall SED-typer som kan inneholde fnr eller dnr`() {
        assertEquals(
            57,
            kanInneholdeIdentEllerFdato.size,
            "Antall SEDer som kan inneholde ident har blitt endret."
        )
    }

    @ParameterizedTest
    @EnumSource(SedType::class)
    fun `Verifiser serde av SedType fungerer`(type: SedType) {
        assertEquals(type, serde(type))
    }

    private fun serde(sedType: SedType): SedType {
        val json = sedType.toJson()

        return mapJsonToAny(json, typeRefs())
    }
}