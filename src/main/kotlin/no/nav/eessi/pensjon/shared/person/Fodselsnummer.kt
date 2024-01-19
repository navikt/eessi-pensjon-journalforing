package no.nav.eessi.pensjon.shared.person

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.shared.person.Fodselsnummer.Companion.tabeller.kontrollsiffer1
import no.nav.eessi.pensjon.shared.person.Fodselsnummer.Companion.tabeller.kontrollsiffer2
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Norwegian national identity number
 *
 * The Norwegian national identity number is an 11-digit personal identifier.
 * Everyone on the Norwegian National Registry has a national identity number.
 *
 * @see <a href="https://www.skatteetaten.no/person/folkeregister/fodsel-og-navnevalg/barn-fodt-i-norge/fodselsnummer">Skatteetaten om fødselsnummer</a>
 * @see <a href="https://github.com/navikt/nav-foedselsnummer">nav-foedselsnummer</a>
 */
class Fodselsnummer private constructor(@JsonValue val value: String) {
    private val logger = LoggerFactory.getLogger(Fodselsnummer::class.java)
    init {
        require("""\d{11}""".toRegex().matches(value)) { "Ikke et gyldig fødselsnummer: $value" }
        require(!erFnr) { "Impelemntasjonen støtter ikke H-nummer og FH-nummer" }
        require(gyldigeKontrollsiffer) { "Ugyldig kontrollnummer" }
    }

    fun getAge(): Int = ChronoUnit.YEARS.between(getBirthDate(), LocalDate.now()).toInt()
    fun getBirthDate(): LocalDate? = foedselsdato
    fun isUnder18Year(): Boolean {
        val resultAge = ChronoUnit.YEARS.between(foedselsdato, LocalDate.now()).toInt()
        return resultAge < 18
    }
    fun isDNumber() = erDnummer
    fun getBirthDateAsIso() = foedselsdato.toString()

    val kjoenn: Kjoenn
        get() {
            val kjoenn = value.slice(8 until 9).toInt()
            return if(kjoenn % 2 == 0) Kjoenn.KVINNE else Kjoenn.MANN
        }

    //De seks første tallene i NPID er tilfeldige tall og har ingen datobetydning.
    //De to første er ikke høyere enn 31. Tall på plass 3 og 4 (måned på FNR) er mellom 21 og 32.
    //De to siste er kontrollsiffer.
    val erNpid: Boolean
        get() {
            val lessThen31 = value.substring(0, 2).toInt()
            val between21and32 = value.substring(2, 4).toInt()
            return lessThen31 in 0..31 && between21and32 in 21..32
        }
    val erDnummer: Boolean
        get() {
            val dag = value[0].toString().toInt()
            return dag in 4..7
        }

    private val syntetiskFoedselsnummerFraNavEllerHNummer: Boolean
        get() {
            return value[2].toString().toInt() in 4..7
        }

    private val syntetiskFoedselsnummerFraSkatteetaten: Boolean
        get() = value[2].toString().toInt() >= 8

    private val erFnr: Boolean
        get() {
            return when(value[0]) {
                '8', '9' -> true
                else -> false
            }
        }

    private val foedselsdato: LocalDate?
        get() {
            if (erNpid) {
                logger.warn("Ugyldig fødselsnummer, fnr er Npid: ${vaskFnr(value)}")
                return null
            }
            val fnrMonth = value.slice(2 until 4).toInt()

            val dayFelt = value.slice(0 until 2).toInt()
            val fnrDay = if(erDnummer) dayFelt - 40 else dayFelt

            val beregnetMaaned =
                if (syntetiskFoedselsnummerFraSkatteetaten) {
                    fnrMonth - 80
                } else if (syntetiskFoedselsnummerFraNavEllerHNummer) {
                    fnrMonth - 40
                } else {
                    fnrMonth
                }

            return LocalDate.of(foedselsaar, beregnetMaaned, fnrDay)
        }

    private val gyldigeKontrollsiffer: Boolean
        get() {
            val ks1 = value[9].toString().toInt()
            val ks2 = value[10].toString().toInt()

            val c1 = checksum(kontrollsiffer1, value)
            if(c1 == 10 || c1 != ks1) {
                return false
            }

            val c2 = checksum(kontrollsiffer2, value)
            return !(c2 == 10 || c2 != ks2)
        }

    private val foedselsaar: Int
        get() {
            val fnrYear = value.slice(4 until 6)
            val individnummer = value.slice(6 until 9).toInt()

            for((individSerie, aarSerie) in tabeller.serier) {
                val kandidat = (aarSerie.start.toString().slice(0 until 2) + fnrYear).toInt()
                if(individSerie.contains(individnummer) && aarSerie.contains(kandidat)) {
                    return kandidat
                }
            }
            throw IllegalStateException("Ugyldig individnummer: $individnummer")
        }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fra(fnr: String?): Fodselsnummer? {
            return try {
                Fodselsnummer(fnr!!.replace(Regex("[^0-9]"), ""))
            } catch (e: Exception) {
                null
            }
        }

        fun vaskFnr(nummer: String?): String? {
            return if(nummer.isNullOrBlank()) null
            else nummer.replace(Regex("""\b\d{11}\b"""), "***")
        }

        fun fraMedValidation(fnr: String?): Fodselsnummer? {
            return try {
                Fodselsnummer(fnr!!.replace(Regex("[^0-9]"), ""))
            } catch (e: Exception) {
                throw e
            }
        }
        object tabeller {
            // https://www.skatteetaten.no/person/folkeregister/fodsel-og-navnevalg/barn-fodt-i-norge/fodselsnummer/
            val serier: List<Pair<ClosedRange<Int>, ClosedRange<Int>>> = listOf(
                500..749 to 1854..1899,
                0..499 to 1900..1999,
                900..999 to 1940..1999,
                500..999 to 2000..2039
            )

            val kontrollsiffer1: List<Int> = listOf(3,7,6,1,8,9,4,5,2)
            val kontrollsiffer2: List<Int> = listOf(5,4,3,2,7,6,5,4,3,2)
        }

        fun checksum(liste: List<Int>, str: String): Int {
            var sum = 0
            for((i, m) in liste.withIndex()) {
                sum += m * str[i].toString().toInt()
            }

            val res = 11 - (sum % 11)
            return if (res == 11) 0 else res
        }
    }

    enum class Kjoenn {
        MANN,
        KVINNE
    }

    override fun equals(other: Any?): Boolean {
        return this.value == (other as Fodselsnummer?)?.value
    }
    override fun hashCode(): Int = this.value.hashCode()
    override fun toString(): String = this.value
}