package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SEDTest {

    @Test
    fun `Test felter på en P8000 deserialiseres korrekt`() {
        val sed = createSedFromFile("/sed/P_BUC_05-P8000.json")

        val person = sed.nav!!.bruker!![0].person

        val pin = person!!.pin!![0]
        assertEquals("22115224755", pin.identifikator)
        assertEquals("NO", pin.land)

        assertEquals("1952-11-22", person.foedselsdato)

        val annenPerson = sed.nav?.annenPerson()!!

        assertEquals("08075327030", annenPerson.pin!![0].identifikator)
        assertEquals("NO", annenPerson.pin!![0].land)

        assertEquals("1953-07-08", annenPerson.foedselsdato)
        assertEquals(Rolle.ETTERLATTE, annenPerson.rolle)
        assertNull(annenPerson.relasjontilavdod)
    }

    @Test
    fun `Test felter på en R005 deserialiseres korrekt`() {
        val sed = createSedFromFile("/sed/R005-alderpensjon-NAV.json")

        val bruker = sed.nav!!.bruker!![0]
        val person = bruker.person!!

        val pin = person.pin!![0]
        assertEquals("04117922400", pin.identifikator)
        assertEquals("NO", pin.land)

        assertEquals("1979-11-04", person.foedselsdato)

        assertEquals("debitor", bruker.tilbakekreving?.status?.type)

        val tilbakekreving = sed.tilbakekreving!!
        assertEquals("alderspensjon", tilbakekreving.feilutbetaling!!.ytelse!!.type)
    }

    @Test
    fun `Test felter på en R_BUC_02 R004 deserialiseres korrekt`() {
        val sed = createSedFromFile("/sed/R_BUC_02_R004.json")

        val bruker = sed.nav?.bruker!!
        assertEquals(2, bruker.size)

        val person1 = bruker[0].person!!
        assertEquals("1998-04-05", person1.foedselsdato)
        assertNull(person1.rolle)

        val eessisak = sed.nav?.eessisak!!
        assertEquals(2, eessisak.size)
        assertEquals(0, eessisak.count { it.land == "NO" })
    }

    @Test
    fun `Test felter på P2000`() {
        val sed = createSedFromFile("/sed/P2000-NAV.json")

        assertNotNull(sed.pensjon?.bruker)
        assertNull(sed.pensjon?.gjenlevende)
        assertEquals(2, sed.pensjon?.ytelser!!.size)
        assertEquals(4, sed.pensjon?.vedlegg!!.size)
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

        assertEquals("alderspensjon", tilbakekreving.feilutbetaling!!.ytelse!!.type)
    }

    private fun createSedFromFile(path: String): SED {
        return mapJsonToAny(javaClass.getResource(path).readText(), typeRefs())
    }
}
