package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TemaTest {

    @Test
    fun serde() {
        assertEquals("\"${Tema.PENSJON.kode}\"", Tema.PENSJON.toJson())
        assertEquals("\"${Tema.UFORETRYGD.kode}\"", Tema.UFORETRYGD.toJson())

        assertEquals(Tema.PENSJON, mapJsonToAny<Tema>("\"PEN\""))
        assertEquals(Tema.UFORETRYGD, mapJsonToAny<Tema>("\"UFO\""))
    }
}