package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.DummySed.Companion.createH070
import no.nav.eessi.pensjon.DummySed.Companion.createP15000
import no.nav.eessi.pensjon.DummySed.Companion.createP2000
import no.nav.eessi.pensjon.DummySed.Companion.createP2100
import no.nav.eessi.pensjon.DummySed.Companion.createP5000
import no.nav.eessi.pensjon.DummySed.Companion.createP8000
import no.nav.eessi.pensjon.DummySed.Companion.createR005
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.Rolle
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FnrHelperTest {

    private lateinit var helper: FnrHelper

    companion object {
        private const val SLAPP_SKILPADDE = "09035225916"
        private const val KRAFTIG_VEGGPRYD = "11067122781"
        private const val LEALAUS_KAKE = "22117320034"
        private const val STERK_BUSK = "12011577847"
    }

    @BeforeEach
    fun setup() {
        helper = FnrHelper()
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                // P2100 som mangler norsk fnr
                createP2100(forsikretFnr = null, gjenlevFnr = null, relasjon = "01"),
                createP2000(forsikretFnr = forventetFnr)
        ))

        val expected = setOf(PersonRelasjon(Fodselsnummer.fra(forventetFnr), relasjon = Relasjon.FORSIKRET, sedType = SedType.P2000))

        assertEquals(1, actual.size)
        assertTrue(actual.containsAll(expected))
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val forventetFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                // P2100 som mangler norsk fnr
                createP2100(forsikretFnr = null, gjenlevFnr = null, relasjon = "01"),
                // P15000 som mangler gyldig gjenlevende fnr, med krav = ALDER
                createP15000(forsikretFnr = forventetFnr, gjenlevFnr = "1234", krav = KravType.ALDER, relasjon = "01")
        ))

        assertEquals(1, actual.size)
        assertEquals(PersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.FORSIKRET, YtelseType.ALDER, SedType.P15000), actual.first())
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                // P2100 som mangler norsk fnr
                createP2100(forsikretFnr = null, gjenlevFnr = null, relasjon = "01"),
                // P15000 som mangler gyldig gjenlevende fnr, med krav = GJENLEV
                createP15000(forsikretFnr = KRAFTIG_VEGGPRYD, gjenlevFnr = "1234", krav = KravType.ETTERLATTE, relasjon = "01")
        ))
        val expectedFnr = Fodselsnummer.fra(KRAFTIG_VEGGPRYD)
        assertEquals(1, actual.size)

        assertEquals(PersonRelasjon(expectedFnr, Relasjon.FORSIKRET, YtelseType.GJENLEV, SedType.P15000), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere personer etter fnr på avdød`() {
        val expectedFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createR005(
                        forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "avdød_mottaker_av_ytelser",
                        annenPersonFnr = expectedFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
                )
        ))

        val enke = PersonRelasjon(Fodselsnummer.fra(expectedFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005)

        assertEquals(1, actual.size)
        assertTrue(actual.contains(enke))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person returnerer fnr`() {
        val expectedFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createR005(expectedFnr, "forsikret_person")
        ))
        assertEquals(1, actual.size)
        assertEquals(PersonRelasjon(Fodselsnummer.fra(expectedFnr), Relasjon.FORSIKRET, sedType = SedType.R005), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person uten pin`() {
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createR005(forsikretFnr = null, forsikretTilbakekreving = "forsikret_person")
        ))
        assertTrue(actual.isEmpty())
    }

    @Test
    fun `Gitt en R_BUC og sed R005 med flere flere personer så returner det en liste med Relasjon`() {
        val forsikretFnr = SLAPP_SKILPADDE
        val annenPersonFnr = KRAFTIG_VEGGPRYD

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createR005(forsikretFnr = forsikretFnr, forsikretTilbakekreving = "debitor",
                        annenPersonFnr = annenPersonFnr, annenPersonTilbakekreving = "debitor")
        ))

        val forste = PersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.ANNET, sedType = SedType.R005)
        val andre = PersonRelasjon(Fodselsnummer.fra(annenPersonFnr), Relasjon.ANNET, sedType = SedType.R005)

        assertEquals(2, actual.size)
        assertTrue(actual.contains(forste))
        assertTrue(actual.contains(andre))
    }

    @Test
    fun `Gitt en R_BUC og flere seder har samme person så returnerer vi en unik liste med en Relasjon`() {
        val actual = helper.getPotensielleFnrFraSeder(
                listOf(
                        createR005(forsikretFnr = KRAFTIG_VEGGPRYD, forsikretTilbakekreving = "debitor"),
                        createH070(KRAFTIG_VEGGPRYD)
                ))
        val forste = PersonRelasjon(Fodselsnummer.fra(KRAFTIG_VEGGPRYD), Relasjon.FORSIKRET, sedType = SedType.H070)

        assertEquals(1, actual.size)
        assertEquals(forste, actual[0])
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere person ikke avdød`() {
        val forventetFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createR005(
                        forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "ikke_noe_som_finnes",
                        annenPersonFnr = forventetFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
                )
        ))
        val enke = PersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005)

        assertEquals(1, actual.size)
        assertEquals(actual[0], enke)
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person debitor alderpensjon returnerer liste med en Relasjon`() {
        val forventetFnr = KRAFTIG_VEGGPRYD

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createR005(forventetFnr, forsikretTilbakekreving = "debitor")
        ))
        val annen = PersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.ANNET, sedType = SedType.R005)

        assertEquals(1, actual.size)
        assertTrue(actual.contains(annen))
    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres kun en gjenlevende uten ytelsestype`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createP5000(forsikretFnr = null, gjenlevFnr = forventetFnr, gjenlevRolle = Rolle.ETTERLATTE),
                createP2100(forsikretFnr = null, gjenlevFnr = null, relasjon = null)
        ))

        val expectedPersonRelasjon = PersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.GJENLEVENDE, null, sedType = SedType.P5000)

        assertEquals(1, actual.size)

        val actualPersonRelasjon = actual.first()
        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres minst en gjenlevende og en avdød`() {
        val gjenlevFnr = LEALAUS_KAKE

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createP2100(forsikretFnr = null, gjenlevFnr = null, relasjon = "03"),
                createP5000(forsikretFnr = "25105424704", gjenlevFnr = gjenlevFnr, relasjon = "02"),
                createP8000(forsikretFnr = "25105424704", annenPersonFnr = gjenlevFnr, rolle = Rolle.ETTERLATTE)
        ))

        val expectedPersonRelasjon = PersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, YtelseType.GJENLEV, SedType.P5000)

        assertEquals(1, actual.size)

        val actualPersonRelasjon = actual.first()
        assertEquals(gjenlevFnr, actualPersonRelasjon.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, actualPersonRelasjon.relasjon)

        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
    }

    @Test
    fun `To personer, to SED P5000 og P15000, med GJENLEV, skal hente gyldige relasjoner fra P15000`() {
        val forsikretFnr = STERK_BUSK
        val gjenlevFnr = LEALAUS_KAKE

        val actual = helper.getPotensielleFnrFraSeder(listOf(
                createP15000(forsikretFnr, gjenlevFnr, KravType.ETTERLATTE, relasjon = "01"),
                createP5000(forsikretFnr, gjenlevFnr)
        ))

        val expectedForsikret = PersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, YtelseType.GJENLEV, sedType = SedType.P15000)
        val expectedGjenlev = PersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, YtelseType.GJENLEV, sedType = SedType.P15000)

        assertEquals(2, actual.size)

        assertEquals(expectedGjenlev, actual.last())
        assertEquals(expectedForsikret, actual.first())
    }

    @Test
    fun `En person, to SED P5000 og P15000, med ALDER, skal hente gyldige relasjoner fra P15000`() {
        val forsikretFnr = KRAFTIG_VEGGPRYD

        val sedList = listOf(
                createP15000(forsikretFnr, gjenlevFnr = null, krav = KravType.ALDER, relasjon = null),
                createP5000(forsikretFnr, gjenlevFnr = null)
        )

        val actual = helper.getPotensielleFnrFraSeder(sedList)

        val expectedPerson = PersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, YtelseType.ALDER, sedType = SedType.P15000)

        assertEquals(1, actual.size)
        assertEquals(expectedPerson, actual.first())
    }

    @Nested
    @DisplayName("Tester for uthenting av fnr i en P2100")
    inner class SedTypeP2100 {

        @Test
        fun `SedType P2100 henter gjenlevende relasjoner, mangler relasjonTilAvdod`() {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(createP2100(forsikretFnr, gjenlevFnr, relasjon = null))

            val relasjoner = FnrHelper().getPotensielleFnrFraSeder(sedList)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertNull(gjenlevRelasjon.ytelseType)
        }

        @ParameterizedTest
        @ValueSource(strings = ["06", "07", "08", "09"])
        fun `SedType P2100 henter gjenlevende relasjoner, har relasjonTilAvdod barn`(relasjon: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(createP2100(forsikretFnr, gjenlevFnr, relasjon))

            val relasjoner = FnrHelper().getPotensielleFnrFraSeder(sedList)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertEquals(YtelseType.BARNEP, gjenlevRelasjon.ytelseType)
        }

        @ParameterizedTest
        @ValueSource(strings = ["01", "02", "03", "04", "05"])
        fun `SedType P2100 henter gjenlevende relasjoner, har annen relasjonTilAvdod`(relasjon: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(createP2100(forsikretFnr, gjenlevFnr, relasjon))

            val relasjoner = FnrHelper().getPotensielleFnrFraSeder(sedList)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertEquals(YtelseType.GJENLEV, gjenlevRelasjon.ytelseType)
        }
    }

    @Nested
    @DisplayName("Tester for uthenting av fnr i en P15000")
    inner class SedTypeP1500 {

        @Test
        fun `SedType P15000 henter gjenlevende hvis krav er 02, relasjon mangler`() {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(
                    createP15000(forsikretFnr, gjenlevFnr, KravType.ETTERLATTE, relasjon = null)
            )

            val relasjoner = FnrHelper().getPotensielleFnrFraSeder(sedList)

            assertEquals(2, relasjoner.size)

            val forsikretRelasjon = relasjoner[0]
            assertEquals(Relasjon.FORSIKRET, forsikretRelasjon.relasjon)
            assertEquals(forsikretFnr, forsikretRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, forsikretRelasjon.sedType)
            assertEquals(YtelseType.GJENLEV, forsikretRelasjon.ytelseType)

            val gjenlevRelasjon = relasjoner[1]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertNull(gjenlevRelasjon.ytelseType)
        }

        @ParameterizedTest
        @ValueSource(strings = ["06", "07", "08", "09"])
        fun `SedType P15000 henter gjenlevende hvis krav er 02, relasjon barn`(relasjon: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(
                    createP15000(forsikretFnr, gjenlevFnr, KravType.ETTERLATTE, relasjon = relasjon)
            )

            val relasjoner = FnrHelper().getPotensielleFnrFraSeder(sedList)

            assertEquals(2, relasjoner.size)

            val forsikretRelasjon = relasjoner[0]
            assertEquals(Relasjon.FORSIKRET, forsikretRelasjon.relasjon)
            assertEquals(forsikretFnr, forsikretRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, forsikretRelasjon.sedType)
            assertEquals(YtelseType.GJENLEV, forsikretRelasjon.ytelseType)

            val gjenlevRelasjon = relasjoner[1]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertEquals(YtelseType.BARNEP, gjenlevRelasjon.ytelseType)
        }


        @ParameterizedTest
        @ValueSource(strings = ["01", "02", "03", "04", "05"])
        fun `SedType P15000 henter gjenlevende hvis krav er 02, annen relasjon`(relasjon: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(
                    createP15000(forsikretFnr, gjenlevFnr, KravType.ETTERLATTE, relasjon = relasjon)
            )

            val relasjoner = FnrHelper().getPotensielleFnrFraSeder(sedList)

            assertEquals(2, relasjoner.size)

            val forsikretRelasjon = relasjoner[0]
            assertEquals(Relasjon.FORSIKRET, forsikretRelasjon.relasjon)
            assertEquals(forsikretFnr, forsikretRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, forsikretRelasjon.sedType)
            assertEquals(YtelseType.GJENLEV, forsikretRelasjon.ytelseType)

            val gjenlevRelasjon = relasjoner[1]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertEquals(YtelseType.GJENLEV, gjenlevRelasjon.ytelseType)
        }
    }

}
