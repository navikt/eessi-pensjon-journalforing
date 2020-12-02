package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate

/**
 * Norwegian national identity number
 *
 * The Norwegian national identity number is an 11-digit personal identifier.
 * Everyone on the Norwegian National Registry has a national identity number.
 *
 * @see <a href="https://www.skatteetaten.no/person/folkeregister/fodsel-og-navnevalg/barn-fodt-i-norge/fodselsnummer">Skatteetaten om fødselsnummer</a>
 */
class Fodselsnummer private constructor(@JsonValue val value: String) {
    private val controlDigits1 = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
    private val controlDigits2 = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

    init {
        require("""\d{11}""".toRegex().matches(value)) { "Ikke et gyldig fødselsnummer: $value" }
        require(!(isHNumber() || isFhNumber())) { "Impelentasjonen støtter ikke H-nummer og FH-nummer" }
        require(validateControlDigits()) { "Ugyldig kontrollnummer" }
    }

    companion object {
        fun fra(s: String?): Fodselsnummer? {
            return try {
                s?.let { Fodselsnummer(s) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * @return birthdate as [LocalDate]
     */
    fun getBirthDate(): LocalDate {
        val month = value.slice(2 until 4).toInt()

        val fnrDay = value.slice(0 until 2).toInt()
        val day = if (isDNumber()) fnrDay - 40 else fnrDay

        return LocalDate.of(getYearOfBirth(), month, day)
    }

    /**
     * @return the birthdate as a ISO 8601 [String]
     */
    fun getBirthDateAsIso() = getBirthDate().toString()

    /**
     * Checks if the identity number is of type D-number.
     *
     * A D-number consists of 11 digits, of which the first six digits show the date of birth,
     * but the first digit is increased by 4.
     */
    fun isDNumber(): Boolean = Character.getNumericValue(value[0]) in 4..7

    /**
     * Calculates year of birth using the individual number.
     *
     * @return 4 digit year of birth as [Int]
     */
    private fun getYearOfBirth(): Int {
        val century: String = when (val individnummer = value.slice(6 until 9).toInt()) {
            in 0..499,
            in 900..999 -> "19"
            in 500..749 -> "18"
            in 500..999 -> "20"
            else -> {
                throw IllegalArgumentException("Ingen gyldig årstall funnet for individnummer $individnummer")
            }
        }

        val year = value.slice(4 until 6)

        return "$century$year".toInt()
    }

    /**
     * Sjekker om fødselsnummeret er av typen "Hjelpenummer".
     *
     * H-nummer er et hjelpenummer, en virksomhetsintern, unik identifikasjon av en person som
     * ikke har fødselsnummer eller D-nummer eller hvor dette er ukjent.
     */
    private fun isHNumber(): Boolean = Character.getNumericValue(value[2]) >= 4

    /**
     * Sjekker om fødselsnummeret er av typen "Felles Nasjonalt Hjelpenummer".
     *
     * Brukes av helsevesenet i tilfeller hvor de har behov for unikt å identifisere pasienter
     * som ikke har et kjent fødselsnummer eller D-nummer.
     */
    private fun isFhNumber(): Boolean = value[0].toInt() in 8..9

    /**
     * Validate control digits.
     */
    private fun validateControlDigits(): Boolean {
        val ks1 = Character.getNumericValue(value[9])

        val c1 = mod(controlDigits1)
        if (c1 == 10 || c1 != ks1) {
            return false
        }

        val c2 = mod(controlDigits2)
        if (c2 == 10 || c2 != Character.getNumericValue(value[10])) {
            return false
        }

        return true
    }

    /**
     * Control Digits 1:
     *  k1 = 11 - ((3 × d1 + 7 × d2 + 6 × m1 + 1 × m2 + 8 × å1 + 9 × å2 + 4 × i1 + 5 × i2 + 2 × i3) mod 11)
     *
     * Control Digits 2
     *  k2 = 11 - ((5 × d1 + 4 × d2 + 3 × m1 + 2 × m2 + 7 × å1 + 6 × å2 + 5 × i1 + 4 × i2 + 3 × i3 + 2 × k1) mod 11)
     */
    private fun mod(arr: IntArray): Int {
        val sum = arr.withIndex()
                .sumBy { (i, m) -> m * Character.getNumericValue(value[i]) }

        val result = 11 - (sum % 11)
        return if (result == 11) 0 else result
    }

    override fun equals(other: Any?): Boolean {
        return this.value == (other as Fodselsnummer?)?.value
    }

    override fun hashCode(): Int = this.value.hashCode()

    override fun toString(): String = this.value
}
