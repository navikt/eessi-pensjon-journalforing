package no.nav.eessi.pensjon.sed

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class SedFnrSøkTest {

    val sedFnrSøk = SedFnrSøk()

    @Test
    fun `Gitt en SED med flere norske fnr i Pin-identifikator feltet når det søkes etter fnr i SED så returner alle norske fnr`() {
        // Gitt
        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))

        // Når
        val funnedeFnr = sedFnrSøk.finnAlleFnrDnrISed(sed)

        // Så
        assertTrue(funnedeFnr.containsAll(listOf("97097097000", "97097097001", "97097097002", "97097097003")))
        assertEquals(funnedeFnr.size, 4)
    }

    @Test
    fun `Gitt en SED med flere norske fnr i Pin-kompetenteuland feltet når det søkes etter fnr i SED så returner alle norske fnr`() {
        // Gitt
        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/sed/H021-NAV.json")))

        // Når
        val funnedeFnr = sedFnrSøk.finnAlleFnrDnrISed(sed)

        // SÅ
        assertTrue(funnedeFnr.containsAll(listOf("12345678910", "12345678990")))
        assertEquals(funnedeFnr.size, 2)
    }
}