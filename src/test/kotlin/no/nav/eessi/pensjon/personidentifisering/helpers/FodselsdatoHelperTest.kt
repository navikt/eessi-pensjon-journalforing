package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

internal class FodselsdatoHelperTest {

    @Test
    fun `Calling getFDatoFromSed returns exception when foedselsdato is not found`() {
        assertThrows<RuntimeException> {
            FodselsdatoHelper.fdatoFraSedListe(listOf(getSedFile("/buc/EmptySED.json")), emptyList())
        }
    }

    @Test
    fun `Calling getFDatoFromSed returns valid fdato when found in first valid SED`() {
        val actual = FodselsdatoHelper.fdatoFraSedListe(
            listOf(
                    getSedFile("/buc/P2100-PinDK-NAV.json"),
                    getSedFile("/buc/P2000-NAV.json"),
                    getSedFile("/buc/P15000-NAV.json")),
            emptyList())
        val expected = LocalDate.of(1969, 9, 11)
        assertEquals(expected, actual)
    }

    @Test
    fun `getFDatoFromSed return RunTimeError when all list is empty`() {
        assertThrows<RuntimeException> {
            FodselsdatoHelper.fdatoFraSedListe(emptyList(), emptyList())
        }
    }

    @Test
    fun `getFDatoFromSed return valid fdato when kansellertList innholder gydlig sed`() {
        val actual = FodselsdatoHelper.fdatoFraSedListe(
            emptyList(),
            listOf(
                getSedFile("/buc/P2100-PinDK-NAV.json"),
                getSedFile("/buc/P2000-NAV.json"),
                getSedFile("/buc/P15000-NAV.json"))
        )
        val expected = LocalDate.of(1969, 9, 11)
        assertEquals(expected, actual)

    }


    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_01` () {
        val sedListe = listOf(getSedFile("/buc/P2000-NAV.json"))
        assertEquals(LocalDate.of(1980, 1, 1), FodselsdatoHelper.fdatoFraSedListe(sedListe, emptyList()))
    }


    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P10000 P_BUC_06` () {
        val sedListe = listOf(getSedFile("/buc/P10000-enkel.json"))
        assertEquals(LocalDate.of(1948, 6, 28), FodselsdatoHelper.fdatoFraSedListe(sedListe, emptyList()))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P10000 superenkel P_BUC_06` () {
        val sedListe = listOf(getSedFile("/buc/P10000-superenkel.json"))
        assertEquals(LocalDate.of(1958, 7, 11), FodselsdatoHelper.fdatoFraSedListe(sedListe, emptyList()))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P10000 person og annenperson P_BUC_06` () {
        val sedListe = listOf(getSedFile("/buc/P10000-person-annenperson.json"))
        assertEquals(LocalDate.of(1986, 1, 29), FodselsdatoHelper.fdatoFraSedListe(sedListe,emptyList()))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset multipleSeds ` () {
        val seds = listOf(
                getSedFile("/buc/EmptySED.json"),
                getSedFile("/buc/EmptySED.json"),
                getSedFile("/buc/P10000-superenkel.json"))

        assertEquals(LocalDate.of(1958, 7, 11), FodselsdatoHelper.fdatoFraSedListe(seds,emptyList()))
    }

    @Test
    fun `ved henting ved fdato på R005 når kun en person hentes personen` () {
        val sedListe = listOf(getSedFile("/sed/R_BUC_02-R005-IkkePin.json"))
        assertEquals(LocalDate.of(1980, 10, 22), FodselsdatoHelper.fdatoFraSedListe(sedListe, emptyList()))
    }

    @Test
    fun `ved henting ved fdato på R005 når det er flere personer og en er avød hentes den avdøde`() {
        val sed = mapJsonToAny(javaClass.getResource("/sed/R005-avdod-enke-NAV.json").readText(), typeRefs<R005>())

        val sedListe = listOf(sed)
        assertEquals(LocalDate.of(2000, 8, 26), FodselsdatoHelper.fdatoFraSedListe(sedListe,emptyList()))
    }

    @Test
    fun `ved henting ved fdato på R005 ved den person som debitor og sak er alderpensjon`() {
        val sed = mapJsonToAny(javaClass.getResource("/sed/R005-alderpensjon-NAV.json").readText(), typeRefs<R005>())
        assertEquals(LocalDate.of(1979, 11, 4), FodselsdatoHelper.fdatoFraSedListe(listOf(sed),emptyList()))
    }

    private fun getSedFile(file: String): SED =  SED.fromJsonToConcrete(javaClass.getResource(file).readText())
}
