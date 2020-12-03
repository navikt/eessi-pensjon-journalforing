package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FdatoHelperTest {

    private lateinit var helper: FdatoHelper

    @BeforeEach
    fun setup() {
        helper = FdatoHelper()
    }

    @Test
    fun `Calling getFDatoFromSed returns exception when foedselsdato is not found` () {
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("buc/EmptySED.json")))
        }
    }

    @Test
    fun `Calling getFDatoFromSed returns valid fdato when found in first valid SED` () {
        val actual = helper.finnEnFdatoFraSEDer(listOf(
                TestUtils.getResource("buc/P2100-PinDK-NAV.json"),
                TestUtils.getResource("buc/P2000-NAV.json"),
                TestUtils.getResource("buc/P15000-NAV.json")))
        val expected = LocalDate.of(1969,9,11)
        assertEquals(expected, actual)
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_01` () {
        assertEquals(LocalDate.of(1980,1,1), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("buc/P2000-NAV.json"))))
    }


    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P10000 P_BUC_06` () {
        assertEquals(LocalDate.of(1948,6,28), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("buc/P10000-enkel.json"))))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P10000 superenkel P_BUC_06` () {
        assertEquals(LocalDate.of(1958,7,11), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("buc/P10000-superenkel.json"))))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P10000 person og annenperson P_BUC_06` () {
        assertEquals(LocalDate.of(1986,1,29), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("buc/P10000-person-annenperson.json"))))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset multipleSeds ` () {
        val seds = listOf(
                TestUtils.getResource("buc/EmptySED.json"),
                TestUtils.getResource("buc/EmptySED.json"),
                TestUtils.getResource("buc/P10000-superenkel.json"))

        assertEquals(LocalDate.of(1958,7,11), helper.finnEnFdatoFraSEDer(seds))
    }

    @Test
    fun `ved henting ved fdato på R005 når kun en person hentes personen` () {
        assertEquals(LocalDate.of(1980,10,22), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("sed/R_BUC_02-R005-IkkePin.json"))))
    }

    @Test
    fun `ved henting ved fdato på R005 når det er flere personer og en er avød hentes den avdøde` () {
        assertEquals(LocalDate.of(2000,8,26), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("sed/R005-avdod-enke-NAV.json"))))
    }

    @Test
    fun `ved henting ved fdato på R005 ved den person som debitor og sak er alderpensjon` () {
        assertEquals(LocalDate.of(1979,11,4), helper.finnEnFdatoFraSEDer(listOf(TestUtils.getResource("sed/R005-alderpensjon-NAV.json"))))
    }
}
