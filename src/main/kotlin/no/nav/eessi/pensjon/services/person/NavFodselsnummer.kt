package no.nav.eessi.pensjon.services.person

import org.slf4j.LoggerFactory

/**
 * fra stash...
 */
class NavFodselsnummer(private val fodselsnummer: String) {

    private val logger = LoggerFactory.getLogger(NavFodselsnummer::class.java)

    init {
        try {
            validate()
        } catch (ex: Exception) {
            throw IllegalArgumentException("Ugydlig fødselsummer lagt inn, kan de inneholde tegn? kun tall")
        }
    }

    fun fnr(): String {
        return fodselsnummer
    }

    fun validate(): Boolean {
        val pnr = Integer.parseInt(getIndividnummer())
        val fnr = Integer.parseInt(getFnr())
        return fnr > 0 && pnr > 0
    }

    private fun getDayInMonth(): String {
        return parseDNumber().substring(0, 2)
    }

    private fun getMonth(): String {
        return parseDNumber().substring(2, 4)
    }

    private fun getFnr(): String {
        return fodselsnummer.substring(0, 5)
    }


    private fun get2DigitBirthYear(): String {
        return fodselsnummer.substring(4, 6)
    }

    fun get4DigitBirthYear(): String {
        return getCentury() + get2DigitBirthYear()
    }

    private fun getCentury(): String {
        val individnummerInt = Integer.parseInt(getIndividnummer())
        val birthYear = Integer.parseInt(get2DigitBirthYear())
        return when {
            (individnummerInt <= 499) -> "19"
            (individnummerInt >= 900 && birthYear > 39) -> "19"
            (individnummerInt >= 500 && birthYear < 40) -> "20"
            (individnummerInt in 500..749 && birthYear > 54) -> "18"
            else -> {
                logger.info("individNr not found ")
                logger.info("Fnr: $fodselsnummer   BirthYear: $birthYear    IndividNr: $individnummerInt")
                throw IllegalArgumentException("Ingen gyldig årstall funnet")
            }
        }
    }

    fun getBirthDateAsISO() = "${get4DigitBirthYear()}-${getMonth()}-${getDayInMonth()}"

    private fun getIndividnummer(): String {
        return fodselsnummer.substring(6, 9)
    }

    private fun parseDNumber(): String {
        return if (!isDNumber()) {
            fodselsnummer
        } else {
            "" + (getFirstDigit() - 4) + fodselsnummer.substring(1)
        }
    }

    fun isDNumber(): Boolean {
        try {
            val firstDigit = getFirstDigit()
            if (firstDigit in 4..7) {
                return true
            }
        } catch (e: IllegalArgumentException) {
            return false
        }
        return false
    }

    fun getFirstDigit(): Int {
        return Integer.parseInt(fodselsnummer.substring(0, 1))
    }
}
