package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Test

internal class SEDTest {


    @Test
    fun balleOgStein() {
        val P8000 = javaClass.getResource("/sed/P_BUC_05-P8000.json").readText()

        val sed1 = mapJsonToAny(P8000, typeRefs<SED>())

        val R005 = javaClass.getResource("/sed/R005-alderpensjon-NAV.json").readText()

        val sed2 = mapJsonToAny(R005, typeRefs<SED>())


        println()
    }

    @Test
    fun tilbakekreving() {
        val json = """
            {
                "anmodning": {
                  "type": "foreløpig"
                },
                "feilutbetaling": {
                  "ytelse": {
                    "type": "alderspensjon"
                  }
                }
              }
        """.trimIndent()

        val tilbakekreving = mapJsonToAny(json, typeRefs<Tilbakekreving>())

        println()
    }
}
