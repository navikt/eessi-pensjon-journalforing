package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.Tilbakekreving
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SEDTest {

    @Test
    fun `Test felter på en P8000 deserialiseres korrekt`() {
        val sed = mapJsonToAny<P8000>(javaClass.getResource("/sed/P_BUC_05-P8000.json").readText())

        val person = sed.nav!!.bruker!!.person

        val pin = person!!.pin!![0]
        assertEquals("22115224755", pin.identifikator)
        assertEquals("NO", pin.land)

        assertEquals("1952-11-22", person.foedselsdato)

        val annenPerson = sed.nav?.annenperson

        assertEquals("11067122781", annenPerson!!.person!!.pin!![0].identifikator)
        assertEquals("NO", annenPerson.person!!.pin!![0].land)

        assertEquals("1971-06-11", annenPerson.person!!.foedselsdato)
        assertEquals(Rolle.ETTERLATTE.kode, annenPerson.person!!.rolle)
        assertNull(annenPerson.person!!.relasjontilavdod)
    }

    @Test
    fun `Test felter på en R005 deserialiseres korrekt`() {
        val sed = mapJsonToAny<R005>(javaClass.getResource("/sed/R005-alderpensjon-NAV.json").readText())

        val brukere = sed.recoveryNav!!.brukere!!
        val person = brukere.firstOrNull()?.person!!

        val pin = person.pin!![0]
        assertEquals("04117922400", pin.identifikator)
        assertEquals("NO", pin.land)

        assertEquals("1979-11-04", person.foedselsdato)

        assertEquals("debitor", brukere.firstOrNull()?.tilbakekreving?.status?.type)

        val tilbakekreving = sed.tilbakekreving!!
        assertEquals("alderspensjon", tilbakekreving.feilutbetaling!!.ytelse!!.type)
    }

    @Test
    fun `Test felter på P2000`() {
        val sed = createSedFromFile("/sed/P2000-NAV.json")

         assertNull(sed.pensjon?.gjenlevende)
        assertEquals(2, sed.pensjon?.ytelser!!.size)
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

        val tilbakekreving = mapJsonToAny<Tilbakekreving>(json)

        assertEquals("alderspensjon", tilbakekreving.feilutbetaling!!.ytelse!!.type)
    }

    private fun createSedFromFile(path: String): SED {
        return mapJsonToAny(javaClass.getResource(path).readText())
    }
}
