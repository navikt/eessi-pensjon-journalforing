package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class R005RelasjonTest : RelasjonTestBase(){

    companion object {
        private const val SLAPP_SKILPADDE = "09035225916"
        private const val KRAFTIG_VEGGPRYD = "11067122781"
        private const val LEALAUS_KAKE = "22117320034"
        private const val STERK_BUSK = "12011577847"
    }

    @Test
    fun hentRelasjoner() {
        val forsikretFnr = SLAPP_SKILPADDE
        val annenPersonFnr = KRAFTIG_VEGGPRYD

        val actual = R005Relasjon(
            createR005(
                forsikretFnr = forsikretFnr, forsikretTilbakekreving = "debitor",
                annenPersonFnr = annenPersonFnr, annenPersonTilbakekreving = "debitor"
            ),
            BucType.R_BUC_02,
        ).hentRelasjoner()

        val forste = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1952,3,9))
        val andre = SEDPersonRelasjon(Fodselsnummer.fra(annenPersonFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11))

        Assertions.assertEquals(2, actual.size)
        assertTrue(actual.contains(forste))
        assertTrue(actual.contains(andre))

    }
}