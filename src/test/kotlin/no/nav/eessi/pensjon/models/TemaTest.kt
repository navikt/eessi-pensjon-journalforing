package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TemaTest {

    @Test
    fun serde() {
        assertEquals("\"${Tema.PENSJON.kode}\"", Tema.PENSJON.toJson())
        assertEquals("\"${Tema.UFORETRYGD.kode}\"", Tema.UFORETRYGD.toJson())

        assertEquals(Tema.PENSJON, mapJsonToAny("\"PEN\"", typeRefs<Tema>()))
        assertEquals(Tema.UFORETRYGD, mapJsonToAny("\"UFO\"", typeRefs<Tema>()))
    }
}