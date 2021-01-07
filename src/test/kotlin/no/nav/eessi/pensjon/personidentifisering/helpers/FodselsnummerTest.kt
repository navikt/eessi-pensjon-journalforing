package no.nav.eessi.pensjon.personidentifisering.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class FodselsnummerTest {

    companion object {
        private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
        private val STERK_BUSK = Fodselsnummer.fra("12011577847")!!
        private val KRAFTIG_VEGGPRYD = Fodselsnummer.fra("11067122781")!!
        private val SLAPP_SKILPADDE = Fodselsnummer.fra("09035225916")!!
        private val GOD_BOLLE = Fodselsnummer.fra("08115525221")!!

        private val DNUMMER_GYLDIG = Fodselsnummer.fra("41060094231")!!
    }

    @Test
    fun `Should be null if invalid value`() {
        assertNull(Fodselsnummer.fra("12011522222"))
        assertNull(Fodselsnummer.fra("01234567890"))
        assertNull(Fodselsnummer.fra("11111111111"))
        assertNull(Fodselsnummer.fra("22222222222"))
        assertNull(Fodselsnummer.fra("19191919191"))
        assertNull(Fodselsnummer.fra(null))
    }

    @Test
    fun `Should remove everything that isnt a digit`() {
        assertNotNull(Fodselsnummer.fra("     22117320034"))            // LEALAUS_KAKE
        assertNotNull(Fodselsnummer.fra("  12011577847     "))          // STERK_BUSK
        assertNotNull(Fodselsnummer.fra("asdf 11067122781 jqwroij"))    // KRAFTIG_VEGGPRYD
        assertNotNull(Fodselsnummer.fra("j-asjd09-035-225916 "))        // SLAPP_SKILPADDE
        assertNotNull(Fodselsnummer.fra("081155 25221"))                // GOD_BOLLE
    }

    @Test
    fun `Validate d-number`() {
        assertTrue(DNUMMER_GYLDIG.isDNumber())
    }

    @Test
    fun `Validate birthdate as ISO 8603`() {
        assertEquals("1973-11-22", LEALAUS_KAKE.getBirthDateAsIso())
        assertEquals("2015-01-12", STERK_BUSK.getBirthDateAsIso())
        assertEquals("1971-06-11", KRAFTIG_VEGGPRYD.getBirthDateAsIso())
        assertEquals("1952-03-09", SLAPP_SKILPADDE.getBirthDateAsIso())
        assertEquals("1955-11-08", GOD_BOLLE.getBirthDateAsIso())
        assertEquals("1900-06-01", DNUMMER_GYLDIG.getBirthDateAsIso())
    }

    @Test
    fun `Validate birthdate as LocalDate`() {
        assertEquals(LocalDate.of(1973, 11, 22), LEALAUS_KAKE.getBirthDate())
        assertEquals(LocalDate.of(2015, 1, 12), STERK_BUSK.getBirthDate())
        assertEquals(LocalDate.of(1971, 6, 11), KRAFTIG_VEGGPRYD.getBirthDate())
        assertEquals(LocalDate.of(1952, 3, 9), SLAPP_SKILPADDE.getBirthDate())
        assertEquals(LocalDate.of(1955, 11, 8), GOD_BOLLE.getBirthDate())
        assertEquals(LocalDate.of(1900, 6, 1), DNUMMER_GYLDIG.getBirthDate())
    }

    @Test
    fun `Validate equals operator`() {
        assertTrue(LEALAUS_KAKE == LEALAUS_KAKE)
        assertTrue(KRAFTIG_VEGGPRYD == KRAFTIG_VEGGPRYD)
        assertTrue(STERK_BUSK == STERK_BUSK)

        assertFalse(SLAPP_SKILPADDE == GOD_BOLLE)
        assertFalse(KRAFTIG_VEGGPRYD == STERK_BUSK)
        assertFalse(LEALAUS_KAKE == GOD_BOLLE)
    }

    @Test
    fun `Validate notEquals operator`() {
        assertTrue(SLAPP_SKILPADDE != GOD_BOLLE)
        assertTrue(KRAFTIG_VEGGPRYD != STERK_BUSK)
        assertTrue(LEALAUS_KAKE != GOD_BOLLE)

        assertFalse(LEALAUS_KAKE != LEALAUS_KAKE)
        assertFalse(KRAFTIG_VEGGPRYD != KRAFTIG_VEGGPRYD)
        assertFalse(STERK_BUSK != STERK_BUSK)
    }
}
