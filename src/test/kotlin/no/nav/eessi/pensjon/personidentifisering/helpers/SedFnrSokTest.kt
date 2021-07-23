package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Kontekst
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Navsak
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.PinLandItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.eux.model.sed.XNav
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SedFnrSokTest {

    @Test
    fun `Gitt en SED med flere norske fnr i Pin-identifikator feltet når det søkes etter fnr i SED så returner alle norske fnr`() {
        // Gitt
        val sedJson = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val sed = mapJsonToAny(sedJson, typeRefs<SED>())

        // Når
        val funnedeFnr = SedFnrSok.finnAlleFnrDnrISed(sed)

        // Så
        assertTrue(funnedeFnr.containsAll(listOf("22117320034", "09035225916", "11067122781", "12011577847")))
        assertEquals(funnedeFnr.size, 4)
    }

    @Test
    fun `SED med fnr under oppholdsland og eller kompetenteuland, gjelder H020 og H021`() {
        val sed = SED(
                type = SedType.H020,
                nav = Nav(
                        bruker = Bruker(person = Person(pinland = PinLandItem(
                                oppholdsland = "22117320034",
                                kompetenteuland = "09035225916"
                        )
                        ))
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(2, resultat.size)
        assertTrue(resultat.containsAll(listOf("22117320034", "09035225916")))
    }

    @Test
    fun `SED med fnr under navsak kontekst, gjelder X005`() {
        val pin = listOf(PinItem(identifikator = "09035225916"))

        val sed = X005(
                type = SedType.X005,
                xnav = XNav(
                        sak = Navsak(Kontekst(Bruker(person = Person(pin))))
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(1, resultat.size)
        assertEquals("09035225916", resultat.first())
    }
}
