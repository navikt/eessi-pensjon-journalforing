package no.nav.eessi.pensjon.personidentifisering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Brukere
import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P2100
import no.nav.eessi.pensjon.eux.model.sed.P6000
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
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresseIFrittFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class PersonidentifiseringServiceTest {

    companion object {
        private const val SLAPP_SKILPADDE = "09035225916"
        private const val LEALAUS_KAKE = "22117320034"
        private const val STERK_BUSK = "12011577847"
    }

    private val personService = mockk<PersonService>(relaxed = true)
    private val personSok = mockk<PersonSok>(relaxed = true)

    private val personidentifiseringService = PersonidentifiseringService(personSok, personService)

//    private val fnrHelper = FnrHelper()

    @Test
    fun `Gitt en H070 der det finnes en p6000 med gjenlevende i samme buc så identifiser forsikret person`() {
        val forsikretFnr = SLAPP_SKILPADDE
        val gjenlevFnr = STERK_BUSK

        every { personService.hentPerson(NorskIdent(gjenlevFnr)) } returns PersonMock.createWith(gjenlevFnr, aktoerId = AktoerId("123213"), landkoder = true)
        every { personService.hentPerson(NorskIdent(forsikretFnr)) } returns PersonMock.createWith(forsikretFnr, aktoerId = AktoerId("321211"), landkoder = true)

        val actual = personidentifiseringService.hentIdentifisertPerson(
            SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, null, SedType.H070, null, rinaDocumentId =  "3123123"), HendelseType.SENDT
        )
        val expected = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, null, sedType = SedType.H070, rinaDocumentId =  "3123123")
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt en Sed som inneholder gjenlevende som ikke er en del av samlingen av Seds som er forsikret, dette er feks H070, H120, H121 så identifiseres en gjenlevende`() {
        val gjenlevFnr = LEALAUS_KAKE
        every { personService.hentPerson(NorskIdent(gjenlevFnr)) } returns PersonMock.createWith(gjenlevFnr, landkoder = true)

        val actual = personidentifiseringService.hentIdentifisertPerson(
            SEDPersonRelasjon(Fodselsnummer.fra(LEALAUS_KAKE), Relasjon.GJENLEVENDE, null, sedType = SedType.P6000, null, rinaDocumentId =  "3123123"), HendelseType.SENDT
        )

        val expected = SEDPersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, null, SedType.P6000, rinaDocumentId =  "3123123")
        assertEquals(expected, actual?.personRelasjon)
    }



    @Test
    fun `Gitt et gyldig fnr og relasjon gjenlevende så skal det identifiseres en person`() {
        every { personService.hentPerson(NorskIdent("05127921999")) } returns PersonMock.createWith("05127921999", landkoder = true)

        val actual = personidentifiseringService.hentIdentifisertPerson(
            SEDPersonRelasjon(Fodselsnummer.fra("05127921999"), Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = SedType.P2100, null, rinaDocumentId =  "3123123"), HendelseType.SENDT
        )
        val expected = SEDPersonRelasjon(Fodselsnummer.fra("05127921999"), Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = SedType.P2100, rinaDocumentId =  "3123123")
        assertEquals(expected, actual?.personRelasjon)
    }



    @Test
    fun `Gitt et gyldig fnr og relasjon forsikret så skal det identifiseres en person`() {
        val fnr = Fodselsnummer.fra("09035225916")!!

        every { personService.hentPerson(NorskIdent(fnr.value)) } returns PersonMock.createWith(fnr.value, landkoder = true)

        val sed = sedFromJsonFile("/buc/P2000-NAV.json")
        val alleSediBuc = listOf(Pair("23123", sed))

        val potensiellePerson = RelasjonsHandler.hentRelasjoner (alleSediBuc, BucType.P_BUC_01)
        val actual = personidentifiseringService.hentIdentifisertePersoner(
            alleSediBuc, BucType.P_BUC_01, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "23123"
        )
        val sokKriterier = SokKriterier("øjøløjøjø","jkljkjl", LocalDate.of(1980, 1, 1))
        val expected = SEDPersonRelasjon(fnr, Relasjon.FORSIKRET, sedType = SedType.P2000, sokKriterier = sokKriterier,  fdato = LocalDate.of(1980, 1, 1),rinaDocumentId =  "23123", saktype = Saktype.ALDER)
        assertEquals(expected, actual.first().personRelasjon)

        verify(exactly = 1) { personService.hentPerson(NorskIdent(fnr.value)) }
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fdato`() {
        val sed = sedFromJsonFile("/buc/P10000-superenkel.json")
        val actual = personidentifiseringService.hentIdentifisertPerson(
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, null, SedType.H070, null, rinaDocumentId = "23123"), HendelseType.SENDT
        )
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed), emptyList())
        assertEquals("1958-07-11", fdato.toString())
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fnr`() {
        val sed1 = sedFromJsonFile("/buc/P10000-superenkel.json")
        val actual = personidentifiseringService.hentIdentifisertPerson(
            SEDPersonRelasjon(null, Relasjon.FORSIKRET, null, SedType.P10000, null, rinaDocumentId = "312321"), HendelseType.SENDT
        )
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed1), emptyList())

        assertEquals("1958-07-11", fdato.toString())

    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
        val navBruker = "11067122781" //avdød bruker fra eux
        val gjenlevende = "09035225916"
        val bucType = BucType.P_BUC_02

        every { personService.hentPerson(NorskIdent(gjenlevende)) } returns PersonMock.createWith(gjenlevende, landkoder = true, fornavn = "Gjenlevende")
        every { personService.hentPerson(NorskIdent(navBruker)) } returns PersonMock.createWith(navBruker, landkoder = true, fornavn = "Avgått-død")

        val sedListe = listOf(
            Pair("231231", SED.generateSedToClass<P2100>(generateSED(SedType.P2100, forsikretFnr = navBruker, gjenlevFnr = gjenlevende, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE))),
            Pair("13231212212A", SED.generateSedToClass<P6000>(generateSED(SedType.P6000, forsikretFnr = navBruker, gjenlevFnr = gjenlevende)))
        )
        val potensiellePerson = RelasjonsHandler.hentRelasjoner (sedListe, BucType.P_BUC_02)

        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(
            sedListe, bucType, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "13231212212A"
        )

        assertEquals(1, identifisertePersoner.size)

        val identifisertRelasjon = identifisertePersoner.single().personRelasjon
        assertEquals(gjenlevende, identifisertRelasjon.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, identifisertRelasjon.relasjon)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(identifisertePersoner, bucType, SedType.P6000, potensiellePerson)

        val gjenlevendeRelasjon = gjenlevActual?.personRelasjon!!
        assertEquals(gjenlevende, gjenlevendeRelasjon.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, gjenlevendeRelasjon.relasjon)

    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 med P2100 og P10000 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
//        val navBruker = "11067122781" //avdød bruker fra eux
//        val gjenlevende = "09035225916"
//        val bucType = BucType.P_BUC_02
//
//        every { personService.hentPerson(NorskIdent(gjenlevende)) } returns PersonMock.createWith(gjenlevende, landkoder = true, fornavn = "Gjenlevende")
//        every { personService.hentPerson(NorskIdent(navBruker)) } returns PersonMock.createWith(navBruker, landkoder = true, fornavn = "Avgått-død")
//
//        val sed1 = SED.generateSedToClass<P2100>(sedFromJsonFile("/buc/P2100-PinNO.json"))
//        val sed2 = SED.generateSedToClass<P6000>(sedFromJsonFile("/buc/P6000-gjenlevende-NAV.json"))
//
//        val sed3 = SED.generateSedToClass<P10000>(sedFromJsonFile("/buc/P10000-person-annenperson.json"))
//
//        val alleSediBuc = listOf(Pair("123123", sed1), Pair("23123123", sed2), Pair("23143-adads-23123", sed3))
//        val potensiellePerson = fnrHelper.getPotensiellePersonRelasjoner(alleSediBuc, BucType.P_BUC_02)
//
//        val actual = personidentifiseringService.hentIdentifisertePersoner(
//            alleSediBuc, bucType, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "23123123"
//        )
//
//        assertEquals(1, actual.size)
//        assertEquals(gjenlevende, actual.first().personRelasjon.fnr!!.value)
//        assertEquals(Relasjon.GJENLEVENDE, actual.first().personRelasjon.relasjon)
//
//        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(actual, bucType, SedType.P10000, potensiellePerson)
//        assertEquals(gjenlevende, gjenlevActual?.personRelasjon?.fnr!!.value)
//        assertEquals(Relasjon.GJENLEVENDE, gjenlevActual.personRelasjon.relasjon)
//
//        verify(exactly = 1) { personService.hentPerson(NorskIdent(gjenlevende)) }
    }

    @Test
    fun `Gitt manglende fnr og en liste med sed som inneholder fdato som gir en gyldig fdato`(){
        val personidentifiseringService2 = PersonidentifiseringService(personSok, personService)

        val sed = sedFromJsonFile("/buc/P10000-superenkel.json")
        val actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed), emptyList())

        assertEquals("1958-07-11", actual.toString())
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder som inneholder fdato som gir en gyldig fdato`() {
        val sed1 = sedFromJsonFile("/buc/P10000-enkel.json")
        val sed2 = sedFromJsonFile("/buc/P2000-NAV.json")

        val actual = personidentifiseringService.hentFodselsDato(null, listOf(sed2, sed1), emptyList())
        assertEquals("1980-01-01", actual.toString())
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder vil returnere en liste size 0`(){
        val sed1 = sedFromJsonFile("/buc/EmptySED.json")
        val alleSediBuc = listOf(Pair("123123", sed1), Pair("23123123", sed1), Pair("23143-adads-23123", sed1))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc,BucType.P_BUC_01)

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            alleSediBuc, BucType.P_BUC_01, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "23123123"
        )
        assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt en tom liste av identifiserte personer når velger person så returner null`(){
        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, BucType.H_BUC_07)
        assertNull(personidentifiseringService.identifisertPersonUtvelger(emptyList(), BucType.H_BUC_07, null, potensiellePerson))
    }

    @Test
    fun `Gitt en liste med en identifisert person når velger person så returner personen`(){
        val identifisertPerson = IdentifisertPerson(
            "123",
            "Testern",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.FORSIKRET, rinaDocumentId = "123123")
        )
        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, BucType.H_BUC_07)

        assertEquals(personidentifiseringService.identifisertPersonUtvelger(listOf(identifisertPerson), BucType.H_BUC_07, SedType.H001, potensiellePerson), identifisertPerson)
    }

    @Test
    fun `Gitt en R_BUC_02 med kun en person når personer identifiseres så returneres første person`(){
        val gjenlevende = IdentifisertPerson(
            "123",
            "Testern",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.GJENLEVENDE, rinaDocumentId = "23123")
        )
        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, BucType.R_BUC_02)

        val result = personidentifiseringService.identifisertPersonUtvelger(listOf(gjenlevende), BucType.R_BUC_02, SedType.R004, potensiellePerson)

        assertEquals(gjenlevende, result)
        assertEquals(1, result?.personListe?.size)
        assertEquals(false , result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt en R_BUC_02 med to hovedpersoner når personer identifiseres så returneres første person`(){
        val avdod = IdentifisertPerson(
            "123",
            "Testern",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.AVDOD, rinaDocumentId = "231231")
        )

        val gjenlevende = IdentifisertPerson(
            "123",
            "Testern",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.GJENLEVENDE, rinaDocumentId = "231231")
        )

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, BucType.R_BUC_02)

        val result = personidentifiseringService.identifisertPersonUtvelger(listOf(avdod, gjenlevende), BucType.R_BUC_02, SedType.R004, potensiellePerson)

        assertEquals(avdod, result)
        assertEquals(2, result?.personListe?.size)
        assertEquals(true , result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt en liste med flere forsikrede på P_BUC_01 så kaster vi en RuntimeException`(){
        val forsikret = IdentifisertPerson(
            "123",
            "Testern",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.FORSIKRET, rinaDocumentId = "23123")
        )

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc,BucType.P_BUC_01)

        assertThrows<FlerePersonPaaBucException> {
            personidentifiseringService.identifisertPersonUtvelger(listOf(forsikret, forsikret, forsikret), BucType.P_BUC_01, SedType.P2000, potensiellePerson)
        }

    }

    @Test
    fun `Gitt at det finnes tre personer når en er gjenlevende så skal kun gjenlevende returneres`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.GJENLEVENDE)

        val list = listOf(person1, person2, person3)

        val alleSediBuc = emptyList<Pair<String, SED>>()
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(alleSediBuc, BucType.P_BUC_02)


        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(Relasjon.GJENLEVENDE, actual?.personRelasjon?.relasjon)
    }

    @Test
    fun `Gitt at det finnes tre personer når ingen personer er gjenlevende så skal returneres null`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, emptyList())
        assertEquals(null, actual)
    }

    @Test
    fun `Gitt personidentifisering identifisere mer enn en person så kastes en runtimeexception`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(emptyList(),BucType.P_BUC_01)

        assertThrows<FlerePersonPaaBucException> {
            personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_01, SedType.P2100, potensiellePerson)
        }

        assertThrows<FlerePersonPaaBucException> {
            personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_03, SedType.P2100, potensiellePerson)
        }
    }

    @Test
//    Scenario 1 - inngående SED, Scenario 2 - utgående SED, Scenario 3 - ingen saksnummer/feil saksnummer
    fun `Gitt det kommer inn SED på R_BUC_02 med flere enn en person Når personer identifiseres Så skal første person returneres`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)

        val result = personidentifiseringService.identifisertPersonUtvelger(list, BucType.R_BUC_02, SedType.P2100, emptyList())

        assertEquals(person1, result)
        assertEquals(3, result?.personListe?.size)
        assertEquals(true, result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt at det ikke finnes personer på en buc så skal kun null returneres`() {
        val actual = personidentifiseringService.identifisertPersonUtvelger(emptyList(), BucType.P_BUC_02, SedType.P2100, emptyList())
        assertEquals(null, actual)
    }

    @Test
    fun `hent ut person gjenlevende fra pBuc02`() {
        val avdodBrukerFnr = Fodselsnummer.fra("02116921297")
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val avdodPerson = IdentifisertPerson(
            "", "avgott Testesen", "NOR", "026123",
            SEDPersonRelasjon(avdodBrukerFnr, Relasjon.FORSIKRET, sedType = SedType.P2100, rinaDocumentId = "23123")
        )

        val sokKriterier = SokKriterier("RASK","MULDVARP", LocalDate.of(1969, 11, 28))
        val gjenlevendePerson = IdentifisertPerson(
            "", "gjenlevende Testesen", "NOR", "026123",
            SEDPersonRelasjon(gjenlevendeFnr, Relasjon.GJENLEVENDE, sedType = SedType.P2100, fdato = LocalDate.of(1969, 11, 28), sokKriterier = sokKriterier, rinaDocumentId = "123123")
        )

        val identifisertePersoner = listOf(avdodPerson, gjenlevendePerson)

        val sed1 = SED.generateSedToClass<P2100>(sedFromJsonFile("/sed/P_BUC_02_P2100_Sendt.json"))
        val sedListFraBuc = listOf(Pair("123123", sed1))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, BucType.P_BUC_02)

        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns PersonMock.createWith(gjenlevendeFnr!!.value, fornavn = "gjenlevende", geo = "026123")
        every { personService.hentPerson(NorskIdent(avdodBrukerFnr!!.value)) } returns PersonMock.createWith(avdodBrukerFnr!!.value, fornavn = "avgott")

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            sedListFraBuc, BucType.P_BUC_02, potensiellePerson, HendelseType.SENDT, rinaDocumentId = ""
        )

        assertEquals(identifisertePersoner[1], actual.single())
    }

    @Test
    fun `hent ut person med landkode utland fra pBuc01`() {
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val sed = SED(SedType.P2000, nav = Nav(bruker = Bruker(person = createPerson(gjenlevendeFnr?.value))))
        val sedListFraBuc = listOf(Pair("12312312", sed))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, BucType.P_BUC_01)

        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns PersonMock.createWith("28116925275", fornavn = "gjenlevende", landkoder = false, etternavn = "Efternamnet")

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            sedListFraBuc, BucType.P_BUC_01, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "12312312"
        )

        val gjenlevperson = actual.first()
        assertEquals("SWE", gjenlevperson.landkode)
    }

    @Test
    fun `hent ut person med landkode fra kontaktaddresse`() {
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val sed = SED(SedType.P2000,nav = Nav(bruker = Bruker(person = createPerson(gjenlevendeFnr?.value))))
        val sedListFraBuc = listOf(Pair("123123", sed))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, BucType.P_BUC_01)

        val genericPerson = PersonMock.createWith("28116925275", fornavn = "gjenlevende", landkoder = true, etternavn = "Efternamnet")
        val person = genericPerson.copy(
            bostedsadresse = null,
            kontaktadresse = Kontaktadresse(utenlandskAdresseIFrittFormat = UtenlandskAdresseIFrittFormat(landkode = "DKK"),
                metadata = genericPerson.navn?.metadata!!, type = KontaktadresseType.Utland)
        )

        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns person

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            sedListFraBuc, BucType.P_BUC_01, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "123123"
        )

        val gjenlevperson = actual.first()
        assertEquals("DKK", gjenlevperson.landkode)
    }

    @Test
    fun `hent ut person med landkode`() {
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val sed = SED(SedType.P2000,nav = Nav(bruker = Bruker(person = createPerson(gjenlevendeFnr?.value))))
        val sedListFraBuc = listOf(Pair("2312321", sed))
        val potensiellePerson = RelasjonsHandler.hentRelasjoner(sedListFraBuc, BucType.P_BUC_01)

        val genericPerson = PersonMock.createWith("28116925275", fornavn = "gjenlevende", landkoder = true, etternavn = "Efternamnet")
        val person = genericPerson.copy(
            bostedsadresse = null, kontaktadresse = null)

        every { personService.harAdressebeskyttelse(any(), any()) } returns false
        every { personService.hentPerson(NorskIdent(gjenlevendeFnr!!.value)) } returns person

        val actual = personidentifiseringService.hentIdentifisertePersoner(
            sedListFraBuc, BucType.P_BUC_01, potensiellePerson, HendelseType.SENDT, rinaDocumentId = "2312321"
        )

        val gjenlevperson = actual.first()
        assertEquals("", gjenlevperson.landkode)
    }

    @Test
    fun `hent ut gjenlevende`() {
        val gjenlevende = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.GJENLEVENDE)
        val avdod = createIdentifisertPerson(Fodselsnummer.fra("5678"), Relasjon.FORSIKRET)

        val list = listOf(gjenlevende, avdod)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, emptyList())

        assertEquals(gjenlevende, actual)
        assertEquals(false, actual?.flereEnnEnPerson())
    }

    @Nested
    @DisplayName("valider indentifisertPerson")
    inner class ValiderIdentifisertPerson {

        @Test
        fun `valider identifisertPerson mottatt caseowner ikke norge fnr og fdato forskjellig gir null ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, HendelseType.MOTTATT, false)
            assertEquals(null, valid)
        }

        @Test
        fun `valider identifisertPerson mottatt caseowner ikke norge fdato er null ident returneres`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE, null)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, HendelseType.MOTTATT, false)
            assertEquals(ident, valid)
        }

        @Test
        fun `valider identifisertPerson mottatt og caseowner ikke norge og fnr og dato er lik gir identifisert person ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE, Fodselsnummer.fra(SLAPP_SKILPADDE)?.getBirthDate())
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, HendelseType.MOTTATT, false)
            assertEquals(ident, valid)
        }

        @Test
        fun `valider identifisertPerson mottatt caseowner norge gir identifisert person ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, HendelseType.MOTTATT, true)
            assertEquals(null, valid)
        }

        @Test
        fun `valider identifisertPerson sendt gir identifisert person ut`() {
            val ident = mockIdentPerson(SLAPP_SKILPADDE)
            val valid = personidentifiseringService.validateIdentifisertPerson(ident, HendelseType.SENDT, true)
            assertEquals(ident, valid)
        }

    }


    private fun mockIdentPerson(fnr: String = SLAPP_SKILPADDE, fdato: LocalDate? = LocalDate.of(1960, 3, 11)) : IdentifisertPerson {
        return IdentifisertPerson(
            "1231231312",
            "Ola Test Testing",
            "NOR",
            "3041",
            SEDPersonRelasjon(Fodselsnummer.fra(fnr), Relasjon.FORSIKRET, Saktype.ALDER, SedType.P2000, null, fdato, rinaDocumentId = "12323")
        )

    }

    private fun sedFromJsonFile(file: String): SED {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    private fun createIdentifisertPerson(fnr: Fodselsnummer?, relasjon: Relasjon): IdentifisertPerson =
        IdentifisertPerson("", "Dummy", "NO", "", SEDPersonRelasjon(fnr, relasjon, rinaDocumentId = "123123"))

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
        )
    }

    /**
     * R005 har "annenPerson" som en sekundær-bruker under Nav-objektet
     */
    private fun createR005(forsikretFnr: String?,
                           forsikretTilbakekreving: String?,
                           annenPersonFnr: String? = null,
                           annenPersonTilbakekreving: String? = null): SED {

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
            recoveryNav = RNav (brukere = listOfNotNull(
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
