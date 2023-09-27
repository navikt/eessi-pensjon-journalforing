package no.nav.eessi.pensjon.personidentifisering.relasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

private const val RINA_NUMMER = "3123134"
internal class RelasjonsHandlerTest : RelasjonTestBase() {

    @Test
    fun `leter igjennom beste Sed på P_BUC_01 etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = RelasjonsHandler.hentRelasjoner(
            listOf(
                // P2100 som mangler norsk fnr
                Pair(
                    "3123131",
                    SED.generateSedToClass<P8000>(
                        generateSED(
                            SedType.P8000,
                            forsikretFnr = null,
                            gjenlevFnr = null,
                            gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
                        )
                    )
                ), Pair(RINA_NUMMER, SED.generateSedToClass<P2000>(generateSED(SedType.P2000, forsikretFnr = forventetFnr)))
            ), P_BUC_01
        )

        val sok = createSokKritere()
        val expected = setOf(
            SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                relasjon = Relasjon.FORSIKRET,
                sedType = SedType.P2000,
                sokKriterier = sok,
                fdato = LocalDate.of(1952, 3, 9),
                rinaDocumentId = RINA_NUMMER,
                saktype = ALDER
            )
        )
        assertEquals(2, actual.size)
        assertTrue(actual.containsAll(expected))
        assertEquals(expected.first(), actual.firstOrNull { it.sedType == SedType.P2000 })
    }

    @Test
    fun `Leter igjennom beste Sed i P_BUC_02 etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val pensjonPerson = Bruker(
            person = Person(
                fornavn = GJENLEV_FNAVN,
                etternavn = ETTERNAVN,
                foedselsdato = "1952-03-09",
                pin = listOf(PinItem(land = "NO", identifikator = forventetFnr)),
                relasjontilavdod = RelasjonAvdodItem(RelasjonTilAvdod.EKTEFELLE.name),
                rolle = Rolle.ETTERLATTE.name
            )
        )
        val p2100Generated = generateSED(
            SedType.P2100,
            forsikretFnr = null,
            gjenlevFnr = null,
            gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
        )

        val p2100 = mockk<P2100>(relaxed = true).apply {
            every { type } returns p2100Generated.type
            every { pensjon?.gjenlevende } returns pensjonPerson
            every { nav?.bruker } returns pensjonPerson
        }

        val actual = RelasjonsHandler.hentRelasjoner(
            listOf(
                // P2100 som mangler norsk fnr
                Pair(
                    RINA_NUMMER,
                    SED.generateSedToClass<P8000>(
                        generateSED(
                            SedType.P8000,
                            forsikretFnr = null,
                            gjenlevFnr = null,
                            gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
                        )
                    )
                ), Pair("2313123", p2100 )
            ), P_BUC_02
        )

        val sok = createSokKritere(fornavn = GJENLEV_FNAVN)
        val expected = setOf(
            SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                relasjon = Relasjon.GJENLEVENDE,
                sedType = SedType.P2100,
                sokKriterier = sok,
                fdato = LocalDate.of(1952, 3, 9),
                rinaDocumentId = "2313123",
                saktype = GJENLEV
            )
        )
        assertEquals(2, actual.size)
        assertEquals(expected.first(), actual.firstOrNull { it.sedType == SedType.P2100 })
    }

    @Nested
    @DisplayName("Tester for uthenting av fnr i en R005 og R_BUC_02")
    inner class SedTypeR005 {

        @Test
        fun `leter igjennom R_BUC_02 og R005 med flere personer etter fnr på avdød`() {
            val expectedFnr = KRAFTIG_VEGGPRYD
            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("3123123", createR005(
                        forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "avdød_mottaker_av_ytelser",
                        annenPersonFnr = expectedFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
                    )
                    )), R_BUC_02
            )

            val enke = SEDPersonRelasjon(Fodselsnummer.fra(expectedFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "3123123")

            assertEquals(2, actual.size)
            assertTrue(actual.contains(enke))
        }

        @Test
        fun `leter igjennom R_BUC_02 og R005 med kun en person returnerer fnr`() {
            val expectedFnr = KRAFTIG_VEGGPRYD
            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("3123123", createR005(expectedFnr, "forsikret_person")
                    )), R_BUC_02
            )
            assertEquals(1, actual.size)
            assertEquals(SEDPersonRelasjon(Fodselsnummer.fra(expectedFnr), Relasjon.FORSIKRET, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "3123123"), actual.first())
        }

        @Test
        fun `leter igjennom R_BUC_02 og R005 med kun en person uten pin`() {
            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("3123123", createR005(forsikretFnr = null, forsikretTilbakekreving = "forsikret_person")
                    )), R_BUC_02
            )
            assertTrue(actual.isEmpty())
        }

        @Test
        fun `Gitt en R_BUC og sed R005 med flere flere personer så returner det en liste med Relasjon`() {
            val forsikretFnr = SLAPP_SKILPADDE
            val annenPersonFnr = KRAFTIG_VEGGPRYD
            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("3123123", createR005(forsikretFnr = forsikretFnr, forsikretTilbakekreving = "debitor",
                        annenPersonFnr = annenPersonFnr, annenPersonTilbakekreving = "debitor"))
                ), R_BUC_02
            )
            assertEquals(0, actual.size)
        }

        @Test
        fun `Gitt en R_BUC og flere seder har samme person så returnerer vi en unik liste med en Relasjon`() {
            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("13123123", createR005(forsikretFnr = KRAFTIG_VEGGPRYD, forsikretTilbakekreving = "debitor")),
                    Pair("23123123", generateSED(SedType.H070, forsikretFnr = KRAFTIG_VEGGPRYD))
                ), R_BUC_02
            )

            val sok = createSokKritere(fdato = LocalDate.of(1971, 6, 11))
            val forste = SEDPersonRelasjon(Fodselsnummer.fra(KRAFTIG_VEGGPRYD),
                Relasjon.FORSIKRET,
                sedType = SedType.H070,
                fdato = sok.foedselsdato,
                rinaDocumentId = "23123123",
                sokKriterier = sok)

            assertEquals(1, actual.size)
            assertEquals(forste, actual[0])
        }

        @Test
        fun `leter igjennom R_BUC_02 og R005 med flere person ikke avdød`() {
            val forventetFnr = KRAFTIG_VEGGPRYD
            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("3123123",
                        createR005(
                            forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "ikke_noe_som_finnes",
                            annenPersonFnr = forventetFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
                        )
                    )
                ), R_BUC_02
            )
            val enke = SEDPersonRelasjon(Fodselsnummer.fra(forventetFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005, fdato = LocalDate.of(1971, 6, 11), rinaDocumentId = "3123123")

            assertEquals(1, actual.size)
            assertEquals(actual[0], enke)
        }

        @Test
        fun `leter igjennom R_BUC_02 og R005 med kun en person debitor alderpensjon returnerer liste med en Relasjon`() {
            val forventetFnr = KRAFTIG_VEGGPRYD
            val actual = RelasjonsHandler.hentRelasjoner(listOf(Pair("3123123",createR005(forventetFnr, forsikretTilbakekreving = "debitor"))), R_BUC_02)

            assertEquals(0, actual.size)
        }

    }

    @Nested
    @DisplayName("Tester for uthenting av fnr i en P2100 og P_BUC_02")
    inner class SedTypeP2100 {

        @Test
        fun `SedType P2100 henter gjenlevende relasjoner, mangler relasjonTilAvdod`() {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(
                Pair(
                    "3123123",
                    SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr, gjenlevFnr = gjenlevFnr, gjenlevRelasjon = null))
                )
            )

            val relasjoner = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_02)

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

            val relasjon = mapJsonToAny<RelasjonTilAvdod>("\"$relasjonKode\"")
            val sedList = listOf(
                Pair(
                    "3123123",
                    SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr, gjenlevFnr = gjenlevFnr, gjenlevRelasjon = relasjon))
                )
            )

            val relasjoner = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_02)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertEquals(BARNEP, gjenlevRelasjon.saktype)
        }

        @ParameterizedTest
        @ValueSource(strings = ["01", "02", "03", "04", "05"])
        fun `SedType P2100 henter gjenlevende relasjoner, har annen relasjonTilAvdod`(relasjonKode: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val relasjon = mapJsonToAny<RelasjonTilAvdod>("\"$relasjonKode\"")
            val sedList = listOf(
                Pair("3123123", SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr, gjenlevFnr = gjenlevFnr, gjenlevRelasjon = relasjon)))
            )

            val relasjoner = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_02)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P2100, gjenlevRelasjon.sedType)
            assertEquals(GJENLEV, gjenlevRelasjon.saktype)
        }

        @Test
        fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres kun en gjenlevende uten ytelsestype`() {
            val forventetFnr = SLAPP_SKILPADDE

            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair(
                        "312312300",
                        SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr = null, gjenlevFnr = forventetFnr, gjenlevRolle = Rolle.ETTERLATTE))
                    ), Pair("312312301", generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = null))
                ), P_BUC_02
            )

            val sok = createSokKritere(GJENLEV_FNAVN, fdato = LocalDate.of(1952, 3, 9))
            val expectedPersonRelasjon = SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                Relasjon.GJENLEVENDE,
                null,
                sedType = SedType.P5000,
                sokKriterier = sok,
                fdato = sok.foedselsdato,
                rinaDocumentId = "312312300"
            )
            assertEquals(1, actual.size)

            val actualPersonRelasjon = actual.first()
            assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
        }

        @Test
        fun `Gitt en P2100 uten gjenlevende når P5000 har en gjenlevende så skal det returneres minst en gjenlevende`() {
            val gjenlevFnr = LEALAUS_KAKE

            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("13123123", generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = null, gjenlevRelasjon = RelasjonTilAvdod.SAMBOER)),
                    Pair(
                        "23123123",
                        SED.generateSedToClass<P5000>(
                            generateSED(
                                SedType.P5000,
                                forsikretFnr = "25105424704",
                                gjenlevFnr = gjenlevFnr,
                                gjenlevRelasjon = RelasjonTilAvdod.PART_I_ET_REGISTRERT_PARTNERSKAP
                            )
                        )
                    ),
                    Pair(
                        "33123123",
                        SED.generateSedToClass<P8000>(
                            generateSED(
                                SedType.P8000,
                                forsikretFnr = "25105424704",
                                annenPersonFnr = gjenlevFnr,
                                forsikretRolle = Rolle.ETTERLATTE
                            )
                        )
                    )
                ), P_BUC_02
            )

            println("*** $actual ***")
            val sok = createSokKritere(GJENLEV_FNAVN, fdato = LocalDate.of(1973, 11, 22))
            val expectedPersonRelasjon = SEDPersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, GJENLEV, SedType.P5000, sokKriterier = sok, fdato = sok.foedselsdato , rinaDocumentId = "23123123")

            assertEquals(2, actual.size)
            val actualPersonRelasjon = actual.first()
            assertEquals(gjenlevFnr, actualPersonRelasjon.fnr!!.value)
            assertEquals(Relasjon.GJENLEVENDE, actualPersonRelasjon.relasjon)

            assertEquals(expectedPersonRelasjon, actualPersonRelasjon)
        }

        @Test
        fun `Gitt en P4000 med forsikret og gjenlevende så velger vi kun gjenlevende relasjoner`() {
            val forventetFnr = SLAPP_SKILPADDE

            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("312312300", SED.generateSedToClass<P4000>(generateSED(SedType.P4000, forsikretFnr = KRAFTIG_VEGGPRYD, gjenlevFnr = forventetFnr)))
                ), P_BUC_02
            )

            val sok = createSokKritere(GJENLEV_FNAVN, fdato = LocalDate.of(1952, 3, 9))
            val expectedPersonRelasjon = SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                Relasjon.GJENLEVENDE,
                null,
                sedType = SedType.P4000,
                sokKriterier = sok,
                fdato = sok.foedselsdato,
                rinaDocumentId = "312312300"
            )
            assertEquals(1, actual.size)
            assertEquals(expectedPersonRelasjon, actual.first())
        }
    }

    @Test
    fun `Gitt en P4000 i P_BUC_02 med forsikret velger vi forsikret relasjon og saksType blir satt til GJENLEV pga buc typen`() {
        val actual = RelasjonsHandler.hentRelasjoner(listOf(
                Pair("312312300", SED.generateSedToClass<P4000>(generateSED(SedType.P4000, forsikretFnr = KRAFTIG_VEGGPRYD, gjenlevFnr = null)))
            ), P_BUC_02
        )

        val sok = createSokKritere(FORSIKRET_FNAVN, fdato = LocalDate.of(1971, 6, 11))
        val expectedPersonRelasjon = SEDPersonRelasjon(
            Fodselsnummer.fra(KRAFTIG_VEGGPRYD),
            Relasjon.FORSIKRET,
            GJENLEV,
            sedType = SedType.P4000,
            sokKriterier = sok,
            fdato = sok.foedselsdato,
            rinaDocumentId = "312312300"
        )
        assertEquals(1, actual.size)
        assertEquals(expectedPersonRelasjon, actual.first())
    }

    @Nested
    @DisplayName("Tester for uthenting av fnr i en P15000 og P_BUC_10")
    inner class SedTypeP12000 {
        @Test
        fun `Gitt en P12000 med forsikret og gjenlevende så velger vi kun gjenlevende relasjoner`() {
            val forventetFnr = SLAPP_SKILPADDE

            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("312312300", SED.generateSedToClass(generateSED(SedType.P12000, forsikretFnr = KRAFTIG_VEGGPRYD, gjenlevFnr = forventetFnr)))
                ), P_BUC_07
            )

            val sok = createSokKritere(GJENLEV_FNAVN, fdato = LocalDate.of(1952, 3, 9))
            val expectedPersonRelasjon = SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                Relasjon.GJENLEVENDE,
                null,
                sedType = SedType.P12000,
                sokKriterier = sok,
                fdato = sok.foedselsdato,
                rinaDocumentId = "312312300"
            )
            assertEquals(1, actual.size)
            assertEquals(expectedPersonRelasjon, actual.first())
        }
        @Test
        fun `Gitt en P12000 med forsikret så velger vi forsikret`() {
            val forventetFnr = KRAFTIG_VEGGPRYD

            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("312312300", SED.generateSedToClass(generateSED(SedType.P12000, forsikretFnr = KRAFTIG_VEGGPRYD)))
                ), P_BUC_07
            )

            val sok = createSokKritere(FORSIKRET_FNAVN, fdato = Fodselsnummer.fra(KRAFTIG_VEGGPRYD)!!.getBirthDate())
            val expectedPersonRelasjon = SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                Relasjon.FORSIKRET,
                null,
                sedType = SedType.P12000,
                sokKriterier = sok,
                fdato = sok.foedselsdato,
                rinaDocumentId = "312312300"
            )
            assertEquals(1, actual.size)
            assertEquals(expectedPersonRelasjon, actual.first())
        }
    }


    @Nested
    @DisplayName("Tester for uthenting av fnr i en P15000 og P_BUC_10")
    inner class SedTypeP15000 {

        @Test
        fun `To personer, to SED P5000 og P15000, med GJENLEV, skal hente gyldige relasjoner fra P15000`() {
            val forsikretFnr = STERK_BUSK
            val gjenlevFnr = LEALAUS_KAKE

            val actual = RelasjonsHandler.hentRelasjoner(
                listOf(
                    Pair("31231231", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.GJENLEV, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE))),
                    Pair("31231233", SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr, gjenlevFnr = gjenlevFnr)))
                ), P_BUC_10
            )

            val sokgjen = createSokKritere(GJENLEV_FNAVN, fdato = LocalDate.of(1973,11,22))
            val expectedGjenlevP15000 = SEDPersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, GJENLEV, sedType = SedType.P15000, sokKriterier = sokgjen, fdato = LocalDate.of(1973,11,22), rinaDocumentId = "31231231")
            assertEquals(1, actual.size)
            assertEquals(expectedGjenlevP15000, actual[0])
        }

        @Test
        fun `En person, to SED P5000 og P15000, med ALDER, skal hente gyldige relasjoner fra P15000`() {
            val forsikretFnr = KRAFTIG_VEGGPRYD

            val sedList = listOf(
                Pair("3123123", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = null, navKrav = KravType.ALDER, gjenlevRelasjon = null))),
                Pair("3123125", SED.generateSedToClass<P5000>(generateSED(SedType.P5000, forsikretFnr, gjenlevFnr = null)))
            )

            val actual = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_01)
            val sok = createSokKritere(fdato = LocalDate.of(1971,6,11))
            val expectedPerson = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, ALDER, sedType = SedType.P15000, sokKriterier = sok, fdato = LocalDate.of(1971, 6,11), rinaDocumentId = "3123123")

            assertEquals(1, actual.size)
            print(expectedPerson)
            assertEquals(expectedPerson, actual.first())
        }

        @Test
        fun `SedType P15000 henter gjenlevende hvis krav er 02, relasjon mangler`() {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val sedList = listOf(
                Pair("3123123",
                    SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.GJENLEV, gjenlevRelasjon = null))
                )
            )

            val relasjoner = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_10)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
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

            val relasjon = mapJsonToAny<RelasjonTilAvdod>("\"$relasjonKode\"")
            val sedList = listOf(
                Pair("3123123", SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.GJENLEV, gjenlevRelasjon = relasjon)))
            )

            val relasjoner = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_10)
            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertEquals(BARNEP, gjenlevRelasjon.saktype)
        }

        @ParameterizedTest
        @ValueSource(strings = ["01", "02", "03", "04", "05"])
        fun `SedType P15000 henter gjenlevende hvis krav er 02, annen relasjon`(relasjonKode: String) {
            val forsikretFnr = SLAPP_SKILPADDE
            val gjenlevFnr = LEALAUS_KAKE

            val relasjon = mapJsonToAny<RelasjonTilAvdod>("\"$relasjonKode\"")
            val sedList = listOf(
                Pair("3123123",SED.generateSedToClass<P15000>(generateSED(SedType.P15000, forsikretFnr = forsikretFnr, gjenlevFnr = gjenlevFnr, navKrav = KravType.GJENLEV, gjenlevRelasjon = relasjon)))
            )

            val relasjoner = RelasjonsHandler.hentRelasjoner(sedList, P_BUC_10)

            assertEquals(1, relasjoner.size)

            val gjenlevRelasjon = relasjoner[0]
            assertEquals(Relasjon.GJENLEVENDE, gjenlevRelasjon.relasjon)
            assertEquals(gjenlevFnr, gjenlevRelasjon.fnr!!.value)
            assertEquals(SedType.P15000, gjenlevRelasjon.sedType)
            assertEquals(GJENLEV, gjenlevRelasjon.saktype)
        }

    }

}