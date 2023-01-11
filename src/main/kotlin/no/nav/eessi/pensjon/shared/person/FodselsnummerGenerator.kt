package no.nav.eessi.pensjon.shared.person

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

object FodselsnummerGenerator {

    private val vekttallProviderFnr1: (Int) -> Int = { arrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2).reversedArray()[it] }
    private val vekttallProviderFnr2: (Int) -> Int = { arrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2).reversedArray()[it] }
    private val fnrDateFormat = DateTimeFormatter.ofPattern("ddMMyy")
    private val KUN_SIFFER = Regex("\\d+")

    /**
     * Ide tatt fra Coronapenger søknad-api https://github.com/navikt/coronapenger-soknad-api
    Kun for generering av fnr for test
     */
    fun generateFnrForTest(alder: Int): String {
        var fnr: String? = null
        while (fnr == null) {
            fnr = genFnr(alder)
            if (fnr.contains("-") || !fnr.erGyldigFodselsnummer()) fnr = null
        }
        return fnr
    }

    private fun String.erKunSiffer() = matches(KUN_SIFFER)
    private fun String.starterMedFodselsdato(): Boolean {
        return try {
            fnrDateFormat.parse(substring(0, 6))
            true
        } catch (cause: Throwable) {
            false
        }
    }

    private fun String.erGyldigFodselsnummer(): Boolean {
        if (length != 11 || !erKunSiffer() || !starterMedFodselsdato()) return false
        val forventetKontrollsifferEn = get(9)
        val kalkulertKontrollsifferEn = Mod11.kontrollsiffer(
            number = substring(0, 9),
            vekttallProvider = vekttallProviderFnr1
        )
        if (kalkulertKontrollsifferEn != forventetKontrollsifferEn) return false
        val forventetKontrollsifferTo = get(10)
        val kalkulertKontrollsifferTo = Mod11.kontrollsiffer(
            number = substring(0, 10),
            vekttallProvider = vekttallProviderFnr2
        )
        return kalkulertKontrollsifferTo == forventetKontrollsifferTo
    }

    private fun genFnr(alder: Int): String {
        val fnrdate = LocalDate.now().minusYears(alder.toLong())
        var year = fnrdate.year
        val dagraw = Random.nextInt(1, 29)
        val maaneraw = Random.nextInt(1, 13)
        //sjekk for om random gir en gyldig dato lik alder.
        val check = LocalDate.of(year, maaneraw, dagraw)
        if (check.isAfter(fnrdate)) {
            year -= 1
        }
        val dag = dagraw.toString().padStart(2, '0')
        val maaned = maaneraw.toString().padStart(2, '0')
        val aar = year.toString().substring(2, year.toString().length)
        val individSiffer = indvididnr(fnrdate.year)
        val utenKontrollSiffer = "$dag$maaned$aar$individSiffer"
        val medForsteKontrollsiffer = utenKontrollSiffer + Mod11.kontrollsiffer(
            number = utenKontrollSiffer,
            vekttallProvider = vekttallProviderFnr1
        )
        //println("fnrdate: $fnrdate, check: $check, year: $year, dag: $dag, måned: $maaned, år: $aar, FNR: $medForsteKontrollsiffer")
        return medForsteKontrollsiffer + Mod11.kontrollsiffer(
            number = medForsteKontrollsiffer,
            vekttallProvider = vekttallProviderFnr2
        )
    }

    internal object Mod11 {
        private val defaultVekttallProvider: (Int) -> Int = { 2 + it % 6 }
        internal fun kontrollsiffer(
            number: String,
            vekttallProvider: (Int) -> Int = defaultVekttallProvider
        ): Char {
            return number.reversed().mapIndexed { i, char ->
                Character.getNumericValue(char) * vekttallProvider(i)
            }.sum().let(Mod11::kontrollsifferFraSum)
        }
        private fun kontrollsifferFraSum(sum: Int) = sum.rem(11).let { rest ->
            when (rest) {
                0 -> '0'
                1 -> '-'
                else -> "${11 - rest}"[0]
            }
        }
    }
    private fun indvididnr(year: Int): String {
        val id = when (year) {
            in 1900..1999 -> "433"
            in 1940..1999 -> "954"
            in 2000..2039 -> "543"
            else -> "739"
        }
        return id
    }
}

