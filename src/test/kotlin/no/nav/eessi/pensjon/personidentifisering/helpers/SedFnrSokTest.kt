package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.Bruker
import no.nav.eessi.pensjon.models.sed.Kontekst
import no.nav.eessi.pensjon.models.sed.Merinformasjon
import no.nav.eessi.pensjon.models.sed.Nav
import no.nav.eessi.pensjon.models.sed.Navsak
import no.nav.eessi.pensjon.models.sed.Pensjon
import no.nav.eessi.pensjon.models.sed.PensjonAvslagItem
import no.nav.eessi.pensjon.models.sed.Person
import no.nav.eessi.pensjon.models.sed.PinItem
import no.nav.eessi.pensjon.models.sed.PinLandItem
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.models.sed.SamletMeldingVedtak
import no.nav.eessi.pensjon.models.sed.YtelserItem
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
                        listOf(Bruker(Person(pinland = PinLandItem(
                                oppholdsland = "22117320034",
                                kompetenteuland = "09035225916"
                        ))))
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(2, resultat.size)
        assertTrue(resultat.containsAll(listOf("22117320034", "09035225916")))
    }

    @Test
    fun `SED med fnr under ytelser`() {
        val sed = SED(
                type = SedType.P8000,
                pensjon = Pensjon(
                        ytelser = listOf(YtelserItem(PinItem(identifikator = "22117320034"))),
                        merinformasjon = Merinformasjon(
                                ytelser = listOf(YtelserItem(PinItem(identifikator = "09035225916")))
                        )
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(2, resultat.size)
        assertTrue(resultat.containsAll(listOf("22117320034", "09035225916")))
    }

    @Test
    fun `SED med fnr under merinformasjon, gjelder P9000 og P100000`() {
        val sed = SED(
                type = SedType.P9000,
                pensjon = Pensjon(
                        merinformasjon = Merinformasjon(
                                Person(listOf(PinItem(identifikator = "22117320034"))),
                                Bruker(Person(listOf(PinItem(identifikator = "09035225916"))))
                        )
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(2, resultat.size)
        assertTrue(resultat.containsAll(listOf("22117320034", "09035225916")))
    }

    @Test
    fun `SED med fnr under vedtak om avslag`() {
        val sed = SED(
                type = SedType.P7000,
                pensjon = Pensjon(
                        samletVedtak = SamletMeldingVedtak(
                                listOf(PensjonAvslagItem("type", PinItem(identifikator = "09035225916")))
                        )
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(1, resultat.size)
        assertEquals("09035225916", resultat.first())
    }

    @Test
    fun `SED med fnr under navsak kontekst, gjelder X005`() {
        val pin = listOf(PinItem(identifikator = "09035225916"))

        val sed = SED(
                type = SedType.X005,
                nav = Nav(
                        sak = Navsak(Kontekst(Bruker(Person(pin))))
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(1, resultat.size)
        assertEquals("09035225916", resultat.first())
    }

    @Test
    fun `SED med fnr under pinannen`() {
        val sed = SED(
                type = SedType.P7000,
                nav = Nav(
                        sak = Navsak(Kontekst(Bruker(Person(
                                pinannen = PinItem(identifikator = "09035225916")
                        ))))
                )
        )

        val resultat = SedFnrSok.finnAlleFnrDnrISed(sed)

        assertEquals(1, resultat.size)
        assertEquals("09035225916", resultat.first())
    }
}
