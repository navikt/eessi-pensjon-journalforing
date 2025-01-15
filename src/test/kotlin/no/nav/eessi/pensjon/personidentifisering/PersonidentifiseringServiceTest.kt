package no.nav.eessi.pensjon.personidentifisering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class PersonidentifiseringServiceTest {

    companion object {
        private const val SLAPP_SKILPADDE = "09035225916"
        private const val LEALAUS_KAKE = "22117320034"
        private const val STERK_BUSK = "12011577847"
    }

    private val personService = mockk<PersonService>(relaxed = false)
    private val personSok = PersonSok(personService)
    private val personidentifiseringService = PersonidentifiseringService(personSok, personService)

    @Test
    fun `Dersom fornavn og  søkkriterie ikke stemmer overens med pdlperson sitt fornavn og eller etternavn saa returneres false`() {
        val sokKriterier = SokKriterier("Johanna M", "Scherer", LocalDate.of(1960, 3, 11))
        val navn = Navn(fornavn = "Johanna Maria", etternavn = "Scherer", metadata = metadata())

        assertTrue(personidentifiseringService.erSokKriterieOgPdlNavnLikt(sokKriterier, navn))
    }

    @Test
    fun `Gitt en P_BUC_02 med gjenlevende og en P8000 med forsikret så skal gjenlevende i P2100 returneres`() {
        val rinaDocumentIdP8000 = "P8000_f899bf659ff04d20bc8b978b186f1ecc_1"
        val fdatoGjenlev = LocalDate.of(2015, 1, 12)
        val fdatoForsikret = LocalDate.of(1973, 11, 22)

        val sokKritereGjenlev = SokKriterier("Gjenlev", "Lever", fdatoGjenlev)
        val sokKritereForsikret = SokKriterier("Forsikret", "Dod", fdatoForsikret)

        val sedPersonRelasjon = listOf(
            SEDPersonRelasjon(
                Fodselsnummer.fra(STERK_BUSK),
                Relasjon.GJENLEVENDE,
                GJENLEV,
                SedType.P2100,
                sokKritereGjenlev,
                fdatoGjenlev,
                rinaDocumentIdP8000
            ),
            SEDPersonRelasjon(
                Fodselsnummer.fra(LEALAUS_KAKE),
                Relasjon.FORSIKRET,
                GJENLEV,
                SedType.P8000,
                sokKritereForsikret,
                fdatoForsikret,
                "P2100_id"
            ),
        )

        every { personService.hentPerson(NorskIdent(STERK_BUSK)) } returns PersonMock.createWith(
            STERK_BUSK,
            landkoder = true
        )
        every { personService.hentPerson(NorskIdent(LEALAUS_KAKE)) } returns PersonMock.createWith(
            LEALAUS_KAKE,
            landkoder = true
        )
        every { personService.sokPerson(any()) } returns setOf(IdentInformasjon(STERK_BUSK, FOLKEREGISTERIDENT))

        val actual = personSok.sokPersonEtterFnr(sedPersonRelasjon, rinaDocumentIdP8000, P_BUC_02, SedType.P8000, SENDT)

        val expected = SEDPersonRelasjon(
            Fodselsnummer.fra(STERK_BUSK),
            Relasjon.GJENLEVENDE,
            GJENLEV,
            SedType.P2100,
            sokKritereGjenlev,
            fdatoGjenlev,
            rinaDocumentIdP8000
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Gitt en H070 der det finnes en p6000 med gjenlevende i samme buc så identifiser forsikret person`() {
        val forsikretFnr = SLAPP_SKILPADDE
        val gjenlevFnr = STERK_BUSK

        every { personService.hentPerson(NorskIdent(gjenlevFnr)) } returns PersonMock.createWith(
            gjenlevFnr,
            aktoerId = AktoerId("123213"),
            landkoder = true
        )
        every { personService.hentPerson(NorskIdent(forsikretFnr)) } returns PersonMock.createWith(
            forsikretFnr,
            aktoerId = AktoerId("321211"),
            landkoder = true
        )

        val actual = personidentifiseringService.hentIdentifisertPersonFraPDL(
            SEDPersonRelasjon(
                Fodselsnummer.fra(forsikretFnr),
                Relasjon.FORSIKRET,
                null,
                SedType.H070,
                rinaDocumentId = "3123123"
            )
        )

        val expected = SEDPersonRelasjon(
            Fodselsnummer.fra(forsikretFnr),
            Relasjon.FORSIKRET,
            null,
            SedType.H070,
            rinaDocumentId = "3123123"
        )
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt en H070 der det finnes en p6000 med NPID i samme buc så identifiser forsikret person`() {
        val forsikretFnr = "01220049651"

        every { personService.hentPerson(Npid(forsikretFnr)) } returns PersonMock.createWith(
            forsikretFnr,
            aktoerId = AktoerId("321211"),
            landkoder = true
        )

        val actual = personidentifiseringService.hentIdentifisertPersonFraPDL(
            SEDPersonRelasjon(
                Fodselsnummer.fra(forsikretFnr),
                Relasjon.FORSIKRET,
                null,
                SedType.H070,
                null,
                rinaDocumentId = "3123123"
            )
        )
        val expected = SEDPersonRelasjon(
            Fodselsnummer.fra(forsikretFnr),
            Relasjon.FORSIKRET,
            null,
            sedType = SedType.H070,
            rinaDocumentId = "3123123"
        )
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Sjekker at isFnrDnrSinFdatoLikSedFdato returnerer dfato som validerer mot fnr `() {

        val actual = personidentifiseringService.validateIdentifisertPerson(
            identifisertPersonPDL(
                aktoerId = "",
                fnr = Fodselsnummer.fra(SLAPP_SKILPADDE),
                personRelasjon = SEDPersonRelasjon(
                    Fodselsnummer.fra(SLAPP_SKILPADDE),
                    Relasjon.GJENLEVENDE,
                    fdato = LocalDate.of(1952, 3, 9),
                    rinaDocumentId = "12"
                )
            ),
            MOTTATT
        )

        assertEquals("1952-03-09", actual?.personRelasjon?.fdato.toString())
    }

    @Test
    fun `Gitt en Sed som inneholder gjenlevende som ikke er en del av samlingen av Seds som er forsikret, dette er feks H070, H120, H121 så identifiseres en gjenlevende`() {
        val gjenlevFnr = LEALAUS_KAKE
        every { personService.hentPerson(NorskIdent(gjenlevFnr)) } returns PersonMock.createWith(
            gjenlevFnr,
            landkoder = true
        )

        val actual = personidentifiseringService.hentIdentifisertPersonFraPDL(
            SEDPersonRelasjon(
                Fodselsnummer.fra(LEALAUS_KAKE),
                Relasjon.GJENLEVENDE,
                null,
                sedType = SedType.P6000,
                null,
                rinaDocumentId = "3123123"
            )
        )

        val expected = SEDPersonRelasjon(
            Fodselsnummer.fra(gjenlevFnr),
            Relasjon.GJENLEVENDE,
            null,
            SedType.P6000,
            rinaDocumentId = "3123123"
        )
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon gjenlevende så skal det identifiseres en person`() {
        every { personService.hentPerson(NorskIdent("05127921999")) } returns PersonMock.createWith(
            "05127921999",
            landkoder = true
        )

        val actual = personidentifiseringService.hentIdentifisertPersonFraPDL(
            SEDPersonRelasjon(
                Fodselsnummer.fra("05127921999"),
                Relasjon.GJENLEVENDE,
                GJENLEV,
                sedType = SedType.P2100,
                null,
                rinaDocumentId = "3123123"
            )
        )
        val expected = SEDPersonRelasjon(
            Fodselsnummer.fra("05127921999"),
            Relasjon.GJENLEVENDE,
            GJENLEV,
            sedType = SedType.P2100,
            rinaDocumentId = "3123123"
        )
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon forsikret så skal det identifiseres en person`() {
        val fnr = Fodselsnummer.fra("09035225916")!!

        every { personService.hentPerson(NorskIdent(fnr.value)) } returns PersonMock.createWith(fnr.value, landkoder = true)

        val sed = sedFromJsonFile("/buc/P2000-NAV.json")
        val alleSediBuc = listOf(Pair("23123", sed))

        val potensiellePerson = RelasjonsHandler.hentRelasjoner (alleSediBuc, P_BUC_01)
        val actual = personidentifiseringService.hentIdentifisertePersoner(
            potensiellePerson
        )
        val sokKriterier = SokKriterier("øjøløjøjø","jkljkjl", LocalDate.of(1980, 1, 1))
        val expected = SEDPersonRelasjon(fnr, Relasjon.FORSIKRET, sedType = SedType.P2000, sokKriterier = sokKriterier,  fdato = LocalDate.of(1980, 1, 1),rinaDocumentId =  "23123", saktype = ALDER)
        assertEquals(expected, actual.first().personRelasjon)

        verify(exactly = 1) { personService.hentPerson(NorskIdent(fnr.value)) }
    }

    @Test
    fun `Gitt et to personer med forskjellige fnr og relasjon forsikret så skal det identifiseres en person dersom de har samme aktoerId`() {
        val fnrPerson1 = Fodselsnummer.fra("09035225916")!!
        val fnrPerson2 = Fodselsnummer.fra("22117320034")!!

        every { personService.hentPerson(any()) } returns PersonMock.createWith(
            fnrPerson1.value,
            landkoder = true,
            aktoerId = AktoerId("123456")
        )

        val sed = sedFromJsonFile("/buc/P2000-NAV.json")
        val alleSediBuc = listOf(Pair("23123", sed))

        val person1 = RelasjonsHandler.hentRelasjoner(alleSediBuc, P_BUC_01)[0].copy(fnr = fnrPerson1)
        val person2 = RelasjonsHandler.hentRelasjoner(alleSediBuc, P_BUC_01)[0].copy(fnr = fnrPerson2)
        val actual = personidentifiseringService.hentIdentifisertePersoner(
            listOf(person1, person2)
        )
        val sokKriterier = SokKriterier("øjøløjøjø", "jkljkjl", LocalDate.of(1980, 1, 1))
        val expected = SEDPersonRelasjon(
            fnrPerson1,
            Relasjon.FORSIKRET,
            sedType = SedType.P2000,
            sokKriterier = sokKriterier,
            fdato = LocalDate.of(1980, 1, 1),
            rinaDocumentId = "23123",
            saktype = ALDER
        )
        assertEquals(expected, actual.first().personRelasjon)

        verify(exactly = 1) { personService.hentPerson(NorskIdent(fnrPerson1.value)) }
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fdato`() {
        val sed = sedFromJsonFile("/buc/P10000-superenkel.json")
        val actual = personidentifiseringService.hentIdentifisertPersonFraPDL(
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, null, SedType.H070, null, rinaDocumentId = "23123")
        )
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed))
        assertEquals("1958-07-11", fdato.toString())
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fnr`() {
        val sed1 = sedFromJsonFile("/buc/P10000-superenkel.json")
        val actual = personidentifiseringService.hentIdentifisertPersonFraPDL(
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, null, SedType.P10000, null, rinaDocumentId = "312321")
        )
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed1))

        assertEquals("1958-07-11", fdato.toString())

    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
        val navBruker = "11067122781" //avdød bruker fra eux
        val gjenlevende = "09035225916"
        val bucType = P_BUC_02

        every { personService.hentPerson(NorskIdent(gjenlevende)) } returns PersonMock.createWith(
            gjenlevende,
            landkoder = true,
            fornavn = "Gjenlevende"
        )
        every { personService.hentPerson(NorskIdent(navBruker)) } returns PersonMock.createWith(
            navBruker,
            landkoder = true,
            fornavn = "Avgått-død"
        )

        val sedListe = listOf(
            Pair(
                "231231",
                SED.generateSedToClass<P2100>(
                    generateSED(
                        SedType.P2100,
                        forsikretFnr = navBruker,
                        gjenlevFnr = gjenlevende,
                        gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
                    )
                )
            ),
            Pair(
                "13231212212A",
                SED.generateSedToClass<P6000>(
                    generateSED(
                        SedType.P6000,
                        forsikretFnr = navBruker,
                        gjenlevFnr = gjenlevende
                    )
                )
            )
        )
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListe, P_BUC_02)

        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(
            potensiellePerson
        )

        assertEquals(1, identifisertePersoner.size)

        val identifisertRelasjon = identifisertePersoner.single().personRelasjon
        assertEquals(gjenlevende, identifisertRelasjon?.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, identifisertRelasjon.relasjon)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(
            identifisertePersoner,
            bucType,
            SedType.P6000,
            potensiellePerson
        )

        val gjenlevendeRelasjon = gjenlevActual?.personRelasjon!!
        assertEquals(gjenlevende, gjenlevendeRelasjon.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, gjenlevendeRelasjon.relasjon)

    }

    @Test
    fun `Gitt en P2100 med gjenlevende og en P8000 med forsikret i P_BUC_02 så skal det hentes to identifiserte personer`() {
        val navBruker = "11067122781" //avdød bruker fra eux
        val gjenlevende = "09035225916"

        every { personService.hentPerson(NorskIdent(gjenlevende)) } returns PersonMock.createWith(
            gjenlevende,
            landkoder = true,
            fornavn = "Gjenlevende"
        )
        every { personService.hentPerson(NorskIdent(navBruker)) } returns PersonMock.createWith(
            navBruker,
            landkoder = true,
            fornavn = "Avgått-død",
            aktoerId = AktoerId("111111")
        )

        val sedListe = listOf(
            Pair(
                "231231",
                SED.generateSedToClass<P2100>(
                    generateSED(
                        SedType.P2100,
                        forsikretFnr = navBruker,
                        gjenlevFnr = gjenlevende,
                        gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
                    )
                )
            ),
            Pair(
                "13231212212A",
                SED.generateSedToClass<P8000>(
                    generateSED(
                        SedType.P8000,
                        forsikretFnr = navBruker,
                        gjenlevFnr = gjenlevende
                    )
                )
            )
        )
        val potensiellePersoner = RelasjonsHandler.hentRelasjoner(sedListe, P_BUC_02)
        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(potensiellePersoner)

        assertEquals(2, identifisertePersoner.size)
    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 med P2100 og P10000 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
        val navBruker = "11067122781" //avdød bruker fra eux
        val gjenlevende = "09035225916"
        val bucType = P_BUC_02

        every { personService.hentPerson(NorskIdent(gjenlevende)) } returns PersonMock.createWith(
            gjenlevende,
            landkoder = true,
            fornavn = "Gjenlevende"
        )
        every { personService.hentPerson(NorskIdent(navBruker)) } returns PersonMock.createWith(
            navBruker,
            landkoder = true,
            fornavn = "Avgått-død"
        )

        val sed1 = SED.generateSedToClass<P2100>(sedFromJsonFile("/buc/P2100-PinNO.json"))
        val sed2 = SED.generateSedToClass<P6000>(sedFromJsonFile("/buc/P6000-gjenlevende-NAV.json"))
        val sed3 = SED.generateSedToClass<P10000>(sedFromJsonFile("/buc/P10000-person-annenperson.json"))

        val alleSediBuc = listOf(Pair("123123", sed1), Pair("23123123", sed2), Pair("23143-adads-23123", sed3))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, P_BUC_02)

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            potensiellePerson
        )

        assertEquals(1, actual.size)
        assertEquals(gjenlevende, actual.first().personRelasjon?.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, actual.first().personRelasjon?.relasjon)

        val gjenlevActual =
            personidentifiseringService.identifisertPersonUtvelger(actual, bucType, SedType.P10000, potensiellePerson)
        assertEquals(gjenlevende, gjenlevActual?.personRelasjon?.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, gjenlevActual.personRelasjon?.relasjon)

        verify(exactly = 1) { personService.hentPerson(NorskIdent(gjenlevende)) }
    }

    @Test
    fun `Gitt manglende fnr og en liste med sed som inneholder fdato som gir en gyldig fdato`() {
        val personidentifiseringService2 = PersonidentifiseringService(personSok, personService)

        val sed = sedFromJsonFile("/buc/P10000-superenkel.json")
        val actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed))

        assertEquals("1958-07-11", actual.toString())
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder som inneholder fdato som gir en gyldig fdato`() {
        val sed1 = sedFromJsonFile("/buc/P10000-enkel.json")
        val sed2 = sedFromJsonFile("/buc/P2000-NAV.json")

        val actual = personidentifiseringService.hentFodselsDato(null, listOf(sed2, sed1))
        assertEquals("1980-01-01", actual.toString())
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder vil returnere en liste size 0`() {
        val sed1 = sedFromJsonFile("/buc/EmptySED.json")
        val alleSediBuc = listOf(Pair("123123", sed1), Pair("23123123", sed1), Pair("23143-adads-23123", sed1))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, P_BUC_01)

        val actual = personidentifiseringService.hentIdentifisertePersoner(potensiellePerson)
        assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt en tom liste av identifiserte personer når velger person så returner null`() {
        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, H_BUC_07)
        assertNull(
            personidentifiseringService.identifisertPersonUtvelger(
                emptyList(),
                H_BUC_07,
                null,
                potensiellePerson
            )
        )
    }

    @Test
    fun `Gitt en liste med en identifisert person når velger person så returner personen`() {
        val identifisertPerson = IdentifisertPDLPerson(
            "123",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.FORSIKRET, rinaDocumentId = "123123"),
            personNavn = "Testern",
            identer = null
        )
        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, H_BUC_07)

        assertEquals(
            personidentifiseringService.identifisertPersonUtvelger(
                listOf(identifisertPerson),
                H_BUC_07,
                SedType.H001,
                potensiellePerson
            ), identifisertPerson
        )
    }

    @Test
    fun `Gitt en R_BUC_02 med kun en person når personer identifiseres så returneres første person`() {
        val gjenlevende = IdentifisertPDLPerson(
            "123",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.GJENLEVENDE, rinaDocumentId = "23123"),
            personNavn = "Testern",
            identer = null
        )
        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, R_BUC_02)

        val result = personidentifiseringService.identifisertPersonUtvelger(
            listOf(gjenlevende),
            R_BUC_02,
            SedType.R004,
            potensiellePerson
        )

        assertEquals(gjenlevende, result)
        assertEquals(0, result?.personListe?.size)
        assertEquals(false, result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt en R_BUC_02 med to hovedpersoner når personer identifiseres så returneres første person`() {
        val avdod = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.AVDOD))
        val gjenlevende = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.GJENLEVENDE))

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, R_BUC_02)

        val result = personidentifiseringService.identifisertPersonUtvelger(
            listOf(avdod, gjenlevende),
            R_BUC_02,
            SedType.R004,
            potensiellePerson
        )

        assertEquals(gjenlevende, result)
        assertEquals(1, result?.personListe?.size)  // AVDOD er med i personlisten
        assertEquals(false, result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt en R_BUC_02 med en FORSIKRET og en GJENLEVENDE saa skal GJENLEVENDE returneres`() {
        val avdod = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.FORSIKRET))
        val gjenlevende = identifisertPDLPerson(sedPersonRelasjon())

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, R_BUC_02)

        val result = personidentifiseringService.identifisertPersonUtvelger(
            listOf(avdod, gjenlevende),
            R_BUC_02,
            SedType.R004,
            potensiellePerson
        )

        assertEquals(gjenlevende, result)
        assertEquals(1, result?.personListe?.size)
        assertEquals(false, result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt en R_BUC_02 med en FORSIKRET og en med relasjon ANNET saa er personlisten tom og GJENLEVENDE returneres`() {
        val annenRelasjon = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.ANNET))
        val forsikret = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.FORSIKRET))

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, R_BUC_02)

        val result = personidentifiseringService.identifisertPersonUtvelger(
            listOf(annenRelasjon, forsikret),
            R_BUC_02,
            SedType.R005,
            potensiellePerson
        )

        assertEquals(forsikret, result)
        assertEquals(0, result?.personListe?.size)
        assertEquals(false, result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt en R_BUC_02 med to FORSIKRET `() {
        val forsikret1 = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.FORSIKRET))
        val forsikret2 = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.FORSIKRET))

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, R_BUC_02)

        assertThrows<FlerePersonPaaBucException> {
            personidentifiseringService.identifisertPersonUtvelger(
                listOf(forsikret1, forsikret2),
                R_BUC_02,
                SedType.R005,
                potensiellePerson
            ).also { println(" her er resultatet: $it") }
        }

    }

    @Test
    fun `Gitt en liste med flere forsikrede på P_BUC_01 så kaster vi en RuntimeException`() {
        val forsikret = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.FORSIKRET))

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, P_BUC_01)

        assertThrows<FlerePersonPaaBucException> {
            personidentifiseringService.identifisertPersonUtvelger(
                listOf(forsikret, forsikret, forsikret),
                P_BUC_01,
                SedType.P2000,
                potensiellePerson
            )
        }
    }

    @Test
    fun `Gitt at det finnes tre personer når en er gjenlevende så skal kun gjenlevende returneres`() {
        val person1 = createIdentifisertPersonPDL(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPersonPDL(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPersonPDL(Fodselsnummer.fra("4567"), Relasjon.GJENLEVENDE)

        val list = listOf(person1, person2, person3)

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, P_BUC_02)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(Relasjon.GJENLEVENDE, actual?.personRelasjon?.relasjon)
    }

    @Test
    fun `Gitt at det finnes tre personer når ingen personer er gjenlevende så skal returneres null`() {
        val person1 = createIdentifisertPersonPDL(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPersonPDL(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPersonPDL(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, P_BUC_02, SedType.P2100, emptyList())
        assertEquals(null, actual)
    }

    @Test
    fun `Gitt personidentifisering identifisere mer enn en person så kastes en runtimeexception`() {
        val person1 = createIdentifisertPersonPDL(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPersonPDL(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPersonPDL(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(emptyList(), P_BUC_01)

        assertThrows<FlerePersonPaaBucException> {
            personidentifiseringService.identifisertPersonUtvelger(list, P_BUC_01, SedType.P2100, potensiellePerson)
        }

    }

    @Test
//    Scenario 1 - inngående SED, Scenario 2 - utgående SED, Scenario 3 - ingen saksnummer/feil saksnummer
    fun `Gitt det kommer inn SED på R_BUC_02 med flere enn en person Når personer identifiseres Så skal første person returneres`() {
        val person1 = createIdentifisertPersonPDL(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPersonPDL(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPersonPDL(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)

            assertThrows<FlerePersonPaaBucException> {
                personidentifiseringService.identifisertPersonUtvelger(list, R_BUC_02, SedType.R005, emptyList())
            }
    }

    @Test
    fun `Gitt det kommer inn SED på R_BUC_02 med flere enn en person saa skal vi kaste FlerePersonPaaBucException`() {
        val person1 = createIdentifisertPersonPDL(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPersonPDL(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person3)

        val relasjoner = listOf(SEDPersonRelasjon(
            fnr = Fodselsnummer.fra("1234"),
            relasjon = Relasjon.ANNET,
            saktype = ALDER,
            sedType = SedType.R005,
            sokKriterier = null,
            fdato = LocalDate.now(),
            rinaDocumentId = "123123"
        ))

        val result = personidentifiseringService.identifisertPersonUtvelger(list, R_BUC_02, SedType.R005, relasjoner)
        val sedRelasjon = result?.personRelasjon?.relasjon.toString()

        assertEquals(person1, result)
        assertEquals(Relasjon.FORSIKRET, result?.personRelasjon?.relasjon)

    }

    @Test
    fun `Gitt at det ikke finnes personer på en buc så skal kun null returneres`() {
        val actual =
            personidentifiseringService.identifisertPersonUtvelger(emptyList(), P_BUC_02, SedType.P2100, emptyList())
        assertEquals(null, actual)
    }

    @Test
    fun `hent ut person gjenlevende fra pBuc02`() {
        val avdodBrukerFnr = Fodselsnummer.fra("02116921297")
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")
        val avdodPerson = identifisertPDLPerson(sedPersonRelasjon(relasjon = Relasjon.FORSIKRET))

        val sokKriterier = SokKriterier("RASK", "MULDVARP", LocalDate.of(1969, 11, 28))
        val gjenlevendePerson = IdentifisertPDLPerson(
            "",
            "NOR",
            "026123",
            SEDPersonRelasjon(
                gjenlevendeFnr,
                Relasjon.GJENLEVENDE,
                sedType = SedType.P2100,
                fdato = LocalDate.of(1969, 11, 28),
                sokKriterier = sokKriterier,
                rinaDocumentId = "123123"
            ),
            personNavn = "gjenlevende Testesen",
            fdato = LocalDate.of(1969, 11, 28),
            identer = gjenlevendeFnr?.let { listOf(it.value) }
        )

        val identifisertePersoner = listOf(avdodPerson, gjenlevendePerson)

        val sed1 = SED.generateSedToClass<P2100>(sedFromJsonFile("/sed/P_BUC_02_P2100_Sendt.json"))
        val sedListFraBuc = listOf(Pair("123123", sed1))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, P_BUC_02)

        every { personService.harAdressebeskyttelse(any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns PersonMock.createWith(
            gjenlevendeFnr!!.value,
            fornavn = "gjenlevende",
            geo = "026123"
        )
        every { personService.hentPerson(NorskIdent(avdodBrukerFnr!!.value)) } returns PersonMock.createWith(
            avdodBrukerFnr!!.value,
            fornavn = "avgott"
        )

        val actual = personidentifiseringService.hentIdentifisertePersoner(potensiellePerson)

        assertEquals(identifisertePersoner[1], actual.single())
    }

    @Test
    fun `hent ut person med landkode utland fra pBuc01`() {
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val sed = SED(SedType.P2000, nav = Nav(bruker = Bruker(person = createPerson(gjenlevendeFnr?.value))))
        val sedListFraBuc = listOf(Pair("12312312", sed))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, P_BUC_01)

        every { personService.harAdressebeskyttelse(any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns PersonMock.createWith(
            "28116925275",
            fornavn = "gjenlevende",
            landkoder = false,
            etternavn = "Efternamnet"
        )

        val actual = personidentifiseringService.hentIdentifisertePersoner(potensiellePerson)

        val gjenlevperson = actual.first()
        assertEquals("SWE", gjenlevperson.landkode)
    }

    @Test
    fun `hent ut person med landkode fra kontaktaddresse`() {
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val sed = SED(SedType.P2000, nav = Nav(bruker = Bruker(person = createPerson(gjenlevendeFnr?.value))))
        val sedListFraBuc = listOf(Pair("123123", sed))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, P_BUC_01)

        val genericPerson =
            PersonMock.createWith("28116925275", fornavn = "gjenlevende", landkoder = true, etternavn = "Efternamnet")
        val person = genericPerson.copy(
            bostedsadresse = null,
            kontaktadresse = Kontaktadresse(
                utenlandskAdresseIFrittFormat = UtenlandskAdresseIFrittFormat(landkode = "DKK"),
                metadata = genericPerson.navn?.metadata!!, type = KontaktadresseType.Utland
            )
        )

        every { personService.harAdressebeskyttelse(any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns person

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            potensiellePerson
        )

        val gjenlevperson = actual.first()
        assertEquals("DKK", gjenlevperson.landkode)
    }

    @Test
    fun `hent ut person med landkode`() {
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val sed = SED(SedType.P2000, nav = Nav(bruker = Bruker(person = createPerson(gjenlevendeFnr?.value))))
        val sedListFraBuc = listOf(Pair("2312321", sed))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, P_BUC_01)

        val genericPerson =
            PersonMock.createWith("28116925275", fornavn = "gjenlevende", landkoder = true, etternavn = "Efternamnet")
        val person = genericPerson.copy(
            bostedsadresse = null, kontaktadresse = null
        )

        every { personService.harAdressebeskyttelse(any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns person

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            potensiellePerson
        )

        val gjenlevperson = actual.first()
        assertEquals("", gjenlevperson.landkode)
    }

    @Test
    fun `hent ut gjenlevende`() {
        val gjenlevende = createIdentifisertPersonPDL(Fodselsnummer.fra("1234"), Relasjon.GJENLEVENDE)
        val avdod = createIdentifisertPersonPDL(Fodselsnummer.fra("5678"), Relasjon.FORSIKRET)

        val list = listOf(gjenlevende, avdod)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, P_BUC_02, SedType.P2100, emptyList())

        assertEquals(gjenlevende, actual)
        assertEquals(false, actual?.flereEnnEnPerson())
    }

    @Nested
    @DisplayName("valider indentifisertPerson")
    inner class ValiderIdentifisertPDLPerson {

        @Test
        fun `valider identifisertPerson mottatt caseowner ikke norge fnr og fdato forskjellig gir null ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, MOTTATT)
            assertEquals(null, valid)
        }

        @Test
        fun `valider identifisertPerson mottatt caseowner ikke norge fdato er null ident returneres`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE, null)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, MOTTATT)
            assertNull(valid)
        }

        @Test
        fun `valider identifisertPerson mottatt og caseowner ikke norge og fnr og dato er lik gir identifisert person ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE, Fodselsnummer.fra(SLAPP_SKILPADDE)?.getBirthDate())
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, MOTTATT)
            assertEquals(ident, valid)
        }

        @Test
        fun `valider identifisertPerson mottatt caseowner norge gir identifisert person ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, MOTTATT)
            assertEquals(null, valid)
        }

        @Test
        fun `valider identifisertPerson sendt gir identifisert person ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, SENDT)
            assertEquals(ident, valid)
        }

        @ParameterizedTest
        @CsvSource(
            "Ola , Testing, Ola, Testing",
            "Ola Mariussen, Testing, Ola, Testing Mariussen",
            "Ola, Testing, Ola, Mariussen Testing",
            "Ola Mariussen, Testing, Ola, Testing",
            "Annette ,BRANGER, ANNETTE Mildred, BRANGER"
        )
        fun `Dersom fornavn og etternavn fra søkkriterie stemmer overens med pdlperson sitt fornavn og etternavn saa returneres true`(
            sokFornavn: String,
            sokEtternavn: String,
            pdlFornavn: String,
            pdlEtternavn: String
        ) {
            val sokKriterier = SokKriterier(sokFornavn, sokEtternavn, LocalDate.of(1960, 3, 11))
            val navn = Navn(fornavn = pdlFornavn, etternavn = pdlEtternavn, metadata = metadata())

            assertTrue(personidentifiseringService.erSokKriterieOgPdlNavnLikt(sokKriterier, navn))

            val sokKriterier1 = SokKriterier("Ola Mariussen", "Testing", LocalDate.of(1960, 3, 11))
            val navn1 = Navn(fornavn = "Ola", etternavn = "Testing Mariussen", metadata = metadata())

            assertTrue(personidentifiseringService.erSokKriterieOgPdlNavnLikt(sokKriterier1, navn1))
        }

        @Test
        fun `Dersom fornavn og eller etternavn fra søkkriterie ikke stemmer overens med pdlperson sitt fornavn og eller etternavn saa returneres false`() {
            val sokKriterier = SokKriterier("Ola", "Testifiserer", LocalDate.of(1960, 3, 11))
            val navn = Navn(fornavn = "Ola", etternavn = "Testing", metadata = metadata())

            assertFalse(personidentifiseringService.erSokKriterieOgPdlNavnLikt(sokKriterier, navn))
        }
    }

    private fun identifisertPDLPerson(sedPersonRelasjon: SEDPersonRelasjon? =  sedPersonRelasjon()) = IdentifisertPDLPerson(
        "123",
        "NO",
        "010",
        sedPersonRelasjon,
        personNavn = "Testern",
        identer = null
    )

    private fun sedPersonRelasjon(relasjon: Relasjon? = Relasjon.GJENLEVENDE, sedType: SedType?= SedType.P2100) =
        SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), relasjon!!, sedType = sedType, rinaDocumentId = "231231")

    private fun metadata() = Metadata(
        endringer = emptyList(),
        historisk = false,
        master = "PDL",
        opplysningsId = "321654987"
    )

    private fun mockIdentPerson(
        fnr: String = SLAPP_SKILPADDE,
        fdato: LocalDate? = LocalDate.of(1960, 3, 11)
    ): IdentifisertPDLPerson {
        return IdentifisertPDLPerson(
            "1231231312",
            "NOR",
            "3041",
            SEDPersonRelasjon(
                Fodselsnummer.fra(fnr),
                Relasjon.FORSIKRET,
                ALDER,
                SedType.P2000,
                null,
                fdato,
                rinaDocumentId = "12323"
            ),
            personNavn = "Ola Test Testing",
            identer = null
        )

    }

    private fun sedFromJsonFile(file: String): SED {
        val json = javaClass.getResource(file)!!.readText()
        return mapJsonToAny(json)
    }

    private fun createIdentifisertPersonPDL(fnr: Fodselsnummer?, relasjon: Relasjon): IdentifisertPDLPerson =
        IdentifisertPDLPerson(
            "",
            "NO",
            "",
            SEDPersonRelasjon(fnr, relasjon, rinaDocumentId = "123123"),
            personNavn = "Dummy",
            identer = null
        )

    fun identifisertPersonPDL(
        aktoerId: String = "32165469879",
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPDLPerson =
        IdentifisertPDLPerson(
            aktoerId,
            landkode,
            geografiskTilknytning,
            personRelasjon,
            fnr,
            personNavn = personNavn,
            identer = null
        )

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
                krav = navKrav?.let { Krav(type = it) }
            ),
            pensjon = gjenlevFnr?.let { createPensjon(gjenlevFnr, gjenlevRelasjon, gjenlevRolle) }
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
