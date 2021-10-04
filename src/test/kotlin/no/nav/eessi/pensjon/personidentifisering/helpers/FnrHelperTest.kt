package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Brukere
import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P2100
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PersonR005
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.RNav
import no.nav.eessi.pensjon.eux.model.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.Status
import no.nav.eessi.pensjon.eux.model.sed.TilbakekrevingBrukere
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

internal class FnrHelperTest {

    private val helper: FnrHelper = FnrHelper()

    companion object {
        private const val SLAPP_SKILPADDE = "09035225916"
        private const val KRAFTIG_VEGGPRYD = "11067122781"
        private const val LEALAUS_KAKE = "22117320034"
        private const val STERK_BUSK = "12011577847"
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                // P2100 som mangler norsk fnr
                Pair("3123131", generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE)),
                Pair("3123134", SED.generateSedToClass<P2000>(generateSED(SedType.P2000, forsikretFnr = forventetFnr)))
            ), BucType.P_BUC_01
        )

        val expected = setOf(SEDPersonRelasjon(Fodselsnummer.fra(forventetFnr), relasjon = Relasjon.FORSIKRET, sedType = SedType.P2000, fdato = LocalDate.of(1952,3,9), rinaDocumentId = "23423"))
        assertEquals(2, actual.size)
        assertTrue(actual.containsAll(expected))
        assertEquals(expected.first(), actual.firstOrNull { it.sedType == SedType.P2000 })
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val forventetFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                // P2100 som mangler norsk fnr
                Pair("3123123",generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE)),
                // P15000 som mangler gyldig gjenlevende fnr, med krav = ALDER
                Pair("3123123", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr = forventetFnr, gjenlevFnr = "1234", navKrav = KravType.ALDER, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE)))
            ), BucType.P_BUC_02
        )

        assertEquals(1, actual.size)
        assertEquals(SEDPersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.FORSIKRET, Saktype.ALDER, SedType.P15000, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "234234"), actual.first())
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                    // P2100 som mangler norsk fnr
                    Pair("3123123", generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE)),
                    // P15000 som mangler gyldig gjenlevende fnr, med krav = GJENLEV
                    Pair("3123123", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr = KRAFTIG_VEGGPRYD, gjenlevFnr = "1234", navKrav = KravType.ETTERLATTE, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE)))
            ), BucType.P_BUC_02
        )
        val expectedFnr = Fodselsnummer.fra(KRAFTIG_VEGGPRYD)
        assertEquals(1, actual.size)

        assertEquals(SEDPersonRelasjon(expectedFnr, Relasjon.FORSIKRET, Saktype.GJENLEV, SedType.P15000, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "234234"), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere personer etter fnr på avdød`() {
        val expectedFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("3123123", createR005(
                            forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "avdød_mottaker_av_ytelser",
                            annenPersonFnr = expectedFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
                    )
            )), BucType.R_BUC_02
        )

        val enke = SEDPersonRelasjon(Fodselsnummer.fra(expectedFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "234234")

        assertEquals(1, actual.size)
        assertTrue(actual.contains(enke))
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person returnerer fnr`() {
        val expectedFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("3123123", createR005(expectedFnr, "forsikret_person")
            )), BucType.R_BUC_02
        )
        assertEquals(1, actual.size)
        assertEquals(SEDPersonRelasjon(Fodselsnummer.fra(expectedFnr), Relasjon.FORSIKRET, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "234234"), actual.first())
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person uten pin`() {
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("3123123", createR005(forsikretFnr = null, forsikretTilbakekreving = "forsikret_person")
            )), BucType.R_BUC_02
        )
        assertTrue(actual.isEmpty())
    }

    @Test
    fun `Gitt en R_BUC og sed R005 med flere flere personer så returner det en liste med Relasjon`() {
        val forsikretFnr = SLAPP_SKILPADDE
        val annenPersonFnr = KRAFTIG_VEGGPRYD

        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("3123123", createR005(forsikretFnr = forsikretFnr, forsikretTilbakekreving = "debitor",
                            annenPersonFnr = annenPersonFnr, annenPersonTilbakekreving = "debitor"))
            ), BucType.R_BUC_02
        )

        val forste = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1952,3,9), rinaDocumentId = "234234")
        val andre = SEDPersonRelasjon(Fodselsnummer.fra(annenPersonFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "234234")

        assertEquals(2, actual.size)
        assertTrue(actual.contains(forste))
        assertTrue(actual.contains(andre))
    }

    @Test
    fun `Gitt en R_BUC og flere seder har samme person så returnerer vi en unik liste med en Relasjon`() {
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("13123123", createR005(forsikretFnr = KRAFTIG_VEGGPRYD, forsikretTilbakekreving = "debitor")),
                Pair("23123123", generateSED(SedType.H070, forsikretFnr = KRAFTIG_VEGGPRYD))
            ), BucType.P_BUC_02
        )
        val forste = SEDPersonRelasjon(Fodselsnummer.fra(KRAFTIG_VEGGPRYD), Relasjon.FORSIKRET, sedType = SedType.H070, fdato = LocalDate.of(1971, 6,11), rinaDocumentId = "234234")

        assertEquals(1, actual.size)
        assertEquals(forste, actual[0])
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere person ikke avdød`() {
        val forventetFnr = KRAFTIG_VEGGPRYD
        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("3123123",
                    createR005(
                            forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "ikke_noe_som_finnes",
                            annenPersonFnr = forventetFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
                    )
                )
            ), BucType.R_BUC_02
        )
        val enke = SEDPersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005, fdato = LocalDate.of(1971, 6, 11), rinaDocumentId = "234234")

        assertEquals(1, actual.size)
        assertEquals(actual[0], enke)
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person debitor alderpensjon returnerer liste med en Relasjon`() {
        val forventetFnr = KRAFTIG_VEGGPRYD

        val actual = helper.getPotensiellePersonRelasjoner(listOf(Pair("3123123",createR005(forventetFnr, forsikretTilbakekreving = "debitor"))), BucType.R_BUC_02)
        val annen = SEDPersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "234234")

        assertEquals(1, actual.size)
        assertTrue(actual.contains(annen))
    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres kun en gjenlevende uten ytelsestype`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("3123123", SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr = null, gjenlevFnr = forventetFnr, gjenlevRolle = Rolle.ETTERLATTE))),
                Pair("3123123",generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = null))
            ), BucType.P_BUC_02
        )

        val expectedPersonRelasjon = SEDPersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.GJENLEVENDE, null, sedType = SedType.P5000, rinaDocumentId = "234234")

        assertEquals(1, actual.size)

        val actualPersonRelasjon = actual.first()
        assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
    }

    @Test
    fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres minst en gjenlevende og en avdød`() {
        val gjenlevFnr = LEALAUS_KAKE

        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("13123123", generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = RelasjonTilAvdod.SAMBOER)),
                Pair("23123123", SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr = "25105424704", gjenlevFnr = gjenlevFnr, gjenlevRelasjon = RelasjonTilAvdod.PART_I_ET_REGISTRERT_PARTNERSKAP))),
                Pair("33123123", SED.generateSedToClass<P8000>(generateSED(SedType.P8000, forsikretFnr = "25105424704", annenPersonFnr = gjenlevFnr, forsikretRolle = Rolle.ETTERLATTE)))
            ), BucType.P_BUC_02
        )
        val expectedPersonRelasjon = SEDPersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, Saktype.GJENLEV, SedType.P5000, rinaDocumentId = "234234")

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

        val actual = helper.getPotensiellePersonRelasjoner(
            listOf(
                Pair("31231231", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.ETTERLATTE, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE))),
                Pair("31231233", SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr, gjenlevFnr = gjenlevFnr)))
            ), BucType.P_BUC_10
        )

        val expectedForsikretP15000 = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, Saktype.GJENLEV, sedType = SedType.P15000, fdato = LocalDate.of(2015,1,12) , rinaDocumentId = "234234")
        val expectedGjenlevP15000 = SEDPersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = SedType.P15000, rinaDocumentId = "234234")
        val expectedGjenlevP5000 = SEDPersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, null, sedType = SedType.P5000, rinaDocumentId = "234234")

        assertEquals(3, actual.size)

        assertEquals(expectedForsikretP15000, actual[0])
        assertEquals(expectedGjenlevP15000, actual[1])
        assertEquals(expectedGjenlevP5000, actual[2])
    }

    @Test
    fun `En person, to SED P5000 og P15000, med ALDER, skal hente gyldige relasjoner fra P15000`() {
        val forsikretFnr = KRAFTIG_VEGGPRYD

        val sedList = listOf(
            Pair("3123123", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = null, navKrav = KravType.ALDER, gjenlevRelasjon = null))),
            Pair("3123123", SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr, gjenlevFnr = null)))
        )

        val actual = helper.getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_01)
        val expectedPerson = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, Saktype.ALDER, sedType = SedType.P15000, fdato = LocalDate.of(1971, 6,11), rinaDocumentId = "234234")

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

            val sedList = listOf(Pair("3123123",SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr, gjenlevFnr = gjenlevFnr, gjenlevRelasjon = null))))

            val relasjoner = FnrHelper().getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_02)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertNull(gjenlevRelasjon.saktype)
        }

        @ParameterizedTest
        @ValueSource(strings = ["06", "07", "08", "09"])
        fun `SedType P2100 henter gjenlevende relasjoner, har relasjonTilAvdod barn`(relasjonKode: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val relasjon = mapJsonToAny("\"$relasjonKode\"", typeRefs<RelasjonTilAvdod>())
            val sedList = listOf(Pair("3123123", SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr, gjenlevFnr = gjenlevFnr, gjenlevRelasjon = relasjon))))

            val relasjoner = FnrHelper().getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_02)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertEquals(Saktype.BARNEP, gjenlevRelasjon.saktype)
        }

        @ParameterizedTest
        @ValueSource(strings = ["01", "02", "03", "04", "05"])
        fun `SedType P2100 henter gjenlevende relasjoner, har annen relasjonTilAvdod`(relasjonKode: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val relasjon = mapJsonToAny("\"$relasjonKode\"", typeRefs<RelasjonTilAvdod>())
            val sedList = listOf(
                Pair("3123123", SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr, gjenlevFnr = gjenlevFnr, gjenlevRelasjon = relasjon))))

            val relasjoner = FnrHelper().getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_02)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertEquals(Saktype.GJENLEV, gjenlevRelasjon.saktype)
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
                Pair("3123123",
                    SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.ETTERLATTE, gjenlevRelasjon = null))
                )
            )

            val relasjoner = FnrHelper().getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_10)

            assertEquals(2, relasjoner.size)

            val forsikretRelasjon = relasjoner[0]
            assertEquals(Relasjon.FORSIKRET, forsikretRelasjon.relasjon)
            assertEquals(forsikretFnr, forsikretRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, forsikretRelasjon.sedType)
            assertEquals(Saktype.GJENLEV, forsikretRelasjon.saktype)

            val gjenlevRelasjon = relasjoner[1]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertNull(gjenlevRelasjon.saktype)
        }

        @ParameterizedTest
        @ValueSource(strings = ["06", "07", "08", "09"])
        fun `SedType P15000 henter gjenlevende hvis krav er 02, relasjon barn`(relasjonKode: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val relasjon = mapJsonToAny("\"$relasjonKode\"", typeRefs<RelasjonTilAvdod>())
            val sedList = listOf(
                Pair("3123123", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.ETTERLATTE, gjenlevRelasjon = relasjon)))
            )

            val relasjoner = FnrHelper().getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_10)
            assertEquals(2, relasjoner.size)

            val forsikretRelasjon = relasjoner[0]
            assertEquals(Relasjon.FORSIKRET, forsikretRelasjon.relasjon)
            assertEquals(forsikretFnr, forsikretRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, forsikretRelasjon.sedType)
            assertEquals(Saktype.GJENLEV, forsikretRelasjon.saktype)

            val gjenlevRelasjon = relasjoner[1]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertEquals(Saktype.BARNEP, gjenlevRelasjon.saktype)
        }


        @ParameterizedTest
        @ValueSource(strings = ["01", "02", "03", "04", "05"])
        fun `SedType P15000 henter gjenlevende hvis krav er 02, annen relasjon`(relasjonKode: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val relasjon = mapJsonToAny("\"$relasjonKode\"", typeRefs<RelasjonTilAvdod>())
            val sedList = listOf(
                Pair("3123123",SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr = forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.ETTERLATTE, gjenlevRelasjon = relasjon)))
            )

            val relasjoner = FnrHelper().getPotensiellePersonRelasjoner(sedList, BucType.P_BUC_10)
            assertEquals(2, relasjoner.size)

            val forsikretRelasjon = relasjoner[0]
            assertEquals(Relasjon.FORSIKRET, forsikretRelasjon.relasjon)
            assertEquals(forsikretFnr, forsikretRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, forsikretRelasjon.sedType)
            assertEquals(Saktype.GJENLEV, forsikretRelasjon.saktype)

            val gjenlevRelasjon = relasjoner[1]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertEquals(Saktype.GJENLEV, gjenlevRelasjon.saktype)
        }
    }

    private fun generateSED(
            sedType: SedType,
            forsikretFnr: String? = null,
            forsikretRolle: Rolle? = null,
            annenPersonFnr: String? = null,
            annenPersonRolle: Rolle? = null,
            navKrav: KravType? = null,
            // Gjenlevende (IKKE annenPerson)
            gjenlevFnr: String? = null,
            gjenlevRolle: Rolle? = null,
            gjenlevRelasjon: RelasjonTilAvdod? = null
    ): SED {
        return SED(
                type = sedType,
                nav = Nav(
                        bruker = Bruker(person = createPerson(forsikretFnr, forsikretRolle)),
                        annenperson = Bruker(person = createPerson(annenPersonFnr, annenPersonRolle)),
                        krav = navKrav?.let { Krav(type = it.kode) }
                ),
                pensjon = gjenlevFnr?.let { createPensjon(gjenlevFnr, gjenlevRelasjon, gjenlevRolle) }
        ).also { println(it.toString()) }
    }

    private fun createR005(forsikretFnr: String?,
                           forsikretTilbakekreving: String?,
                           annenPersonFnr: String? = null,
                           annenPersonTilbakekreving: String? = null): R005 {

        val annenPerson = annenPersonFnr?.let {
            Brukere(
                    person = createPersonR005(it),
                    tilbakekreving = annenPersonTilbakekreving?.let { type ->
                        TilbakekrevingBrukere(status = Status(type))
                    }
            )
        }

        return R005(
                type = SedType.R005,
                recoveryNav = RNav(brukere = listOfNotNull(
                        Brukere(
                                person = createPersonR005(forsikretFnr),
                                tilbakekreving = forsikretTilbakekreving?.let {
                                    TilbakekrevingBrukere(status = Status(it))
                                }
                        ),
                        annenPerson
                ))
        )
    }

    private fun createPersonR005(fnr: String?, rolle: Rolle? = null): PersonR005 {
        return PersonR005(
                rolle = rolle?.name,
                foedselsdato = Fodselsnummer.fra(fnr)?.getBirthDateAsIso() ?: "1955-09-12",
                pin = listOfNotNull(
                        PinItem(land = "DE", identifikator = "1234567"), // Ugyldig utland
                        fnr?.let { PinItem(land = "NO", identifikator = fnr) }
                )
        )
    }

    private fun createPerson(fnr: String?, rolle: Rolle? = null): Person {
        return Person(
            rolle = rolle?.name,
            foedselsdato = Fodselsnummer.fra(fnr)?.getBirthDateAsIso() ?: "1955-09-12",
            pin = listOfNotNull(
                PinItem(land = "DE", identifikator = "1234567"), // Ugyldig utland
                fnr?.let { PinItem(land = "NO", identifikator = fnr) }
            )
        )
    }

    private fun createPensjon(gjenlevFnr: String?, relasjon: RelasjonTilAvdod?, rolle: Rolle? = null): Pensjon =
            Pensjon(
                    gjenlevende = Bruker(
                            person = Person(
                                    pin = listOf(PinItem(land = "NO", identifikator = gjenlevFnr)),
                                    relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) },
                                    rolle = rolle?.name
                            )
                    )
            )
}
