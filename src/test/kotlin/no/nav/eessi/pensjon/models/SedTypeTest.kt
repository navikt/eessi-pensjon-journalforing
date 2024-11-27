package no.nav.eessi.pensjon.models


import no.nav.eessi.pensjon.eux.SedTypeUtils.typerMedIdentEllerFDato
import no.nav.eessi.pensjon.eux.SedTypeUtils.ugyldigeTyper
import no.nav.eessi.pensjon.eux.model.SedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class SedTypeTest {

    @ParameterizedTest
    @EnumSource(
        SedType::class, names = [
        "SEDTYPE_P13000", "SEDTYPE_X001", "SEDTYPE_X002", "SEDTYPE_X003", "SEDTYPE_X004", "SEDTYPE_X006", "SEDTYPE_X007",
        "SEDTYPE_X009", "SEDTYPE_X011", "SEDTYPE_X012", "SEDTYPE_X013", "SEDTYPE_X050", "SEDTYPE_X100",
        "SEDTYPE_H001", "SEDTYPE_H002", "SEDTYPE_H020", "SEDTYPE_H021", "SEDTYPE_H120", "SEDTYPE_H121", "SEDTYPE_R006"
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
            81,
            SedType.values().size,
            "Antall SED-typer har blitt endret."
        )
    }

    @Test
    fun `Sjekk antall ugyldige SED-typer`() {
        assertEquals(
            20,
            ugyldigeTyper.size,
            "Antall ugyldige SED-typer har blitt endret."
        )
    }

    @Test
    fun `Sjekk antall SED-typer som kan inneholde fnr eller dnr`() {
        assertEquals(
            63,
            typerMedIdentEllerFDato.size,
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

        return SedType.fromJson(json)
    }
}