package no.nav.eessi.pensjon.personidentifisering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.Bruker
import no.nav.eessi.pensjon.models.sed.Krav
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.Nav
import no.nav.eessi.pensjon.models.sed.Pensjon
import no.nav.eessi.pensjon.models.sed.Person
import no.nav.eessi.pensjon.models.sed.PinItem
import no.nav.eessi.pensjon.models.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.models.sed.Rolle
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.models.sed.Status
import no.nav.eessi.pensjon.models.sed.Tilbakekreving
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.BrukerMock
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonidentifiseringServiceTest {

    companion object {
        private const val SLAPP_SKILPADDE = "09035225916"
        private const val LEALAUS_KAKE = "22117320034"
        private const val STERK_BUSK = "12011577847"
    }

    @Mock
    private lateinit var aktoerregisterService: AktoerregisterService

    @Mock
    private lateinit var personV3Service: PersonV3Service

    @Mock
    private lateinit var diskresjonkodeHelper: DiskresjonkodeHelper

    private lateinit var personidentifiseringService: PersonidentifiseringService

    @BeforeEach
    fun setup() {
        whenever(aktoerregisterService.hentGjeldendeIdent(eq(IdentGruppe.AktoerId), any<NorskIdent>()))
                .thenReturn(null)

        personidentifiseringService = PersonidentifiseringService(aktoerregisterService,
                personV3Service,
                diskresjonkodeHelper,
                FnrHelper())
    }

    @Test
    fun `Gitt en H070 der det finnes en p6000 med gjenlevende i samme buc så identifiser forsikret person`() {
        val forsikretFnr = SLAPP_SKILPADDE
        val gjenlevFnr = STERK_BUSK
        whenever(personV3Service.hentPerson(eq(gjenlevFnr))).thenReturn(BrukerMock.createWith(landkoder = true))
        whenever(personV3Service.hentPerson(eq(forsikretFnr))).thenReturn(BrukerMock.createWith(landkoder = true))

        val p6000 = generateSED(SedType.P6000, LEALAUS_KAKE, gjenlevFnr = gjenlevFnr)
        val h070 = generateSED(SedType.H070, forsikretFnr)

        val actual = personidentifiseringService.hentIdentifisertPerson(null, listOf(p6000, h070), BucType.P_BUC_05, SedType.H070)
        val expected = PersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.FORSIKRET, null, sedType = SedType.H070)
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt en Sed som inneholder gjenlevende som ikke er en del av samlingen av Seds som er forsikret, dette er feks H070, H120, H121 så identifiseres en gjenlevende`() {
        val gjenlevFnr = LEALAUS_KAKE
        whenever(personV3Service.hentPerson(eq(gjenlevFnr))).thenReturn(BrukerMock.createWith(landkoder = true))

        val p6000 = generateSED(SedType.P6000, STERK_BUSK, gjenlevFnr = gjenlevFnr)
        val actual = personidentifiseringService.hentIdentifisertPerson(null, listOf(p6000), BucType.P_BUC_05, SedType.P6000)

        val expected = PersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, null, SedType.P6000)
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon gjenlevende så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("05127921999"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val sed = generateSED(SedType.P2100, forsikretFnr = null, gjenlevFnr = "05127921999", gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE)
        val actual = personidentifiseringService.hentIdentifisertPerson(null, listOf(sed), BucType.P_BUC_02, SedType.H070)
        val expected = PersonRelasjon(Fodselsnummer.fra("05127921999"), Relasjon.GJENLEVENDE, YtelseType.GJENLEV, sedType = SedType.P2100)
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon avdod så skal det identifiseres en person`() {
        val gjenlevFnr = LEALAUS_KAKE

        whenever(personV3Service.hentPerson(eq(gjenlevFnr))).thenReturn(BrukerMock.createWith(landkoder = true))

        val sed = createR005(
                forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "avdød_mottaker_av_ytelser",
                annenPersonFnr = gjenlevFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
        )
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(listOf(sed))
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, listOf(sed), BucType.R_BUC_02, potensiellePerson)
        val expected = PersonRelasjon(Fodselsnummer.fra(gjenlevFnr), Relasjon.GJENLEVENDE, sedType = SedType.R005)

        val person = actual.single()
        assertEquals(expected, person.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon forsikret så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("09035225916"))).thenReturn(BrukerMock.createWith(landkoder = true))
        whenever(diskresjonkodeHelper.hentDiskresjonskode(any())).thenReturn(null)

        val sed = sedFromJsonFile("/buc/P2000-NAV.json")
        val alleSediBuc = listOf(sed)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, alleSediBuc, BucType.P_BUC_01, potensiellePerson)
        val expected = PersonRelasjon(Fodselsnummer.fra("09035225916"), Relasjon.FORSIKRET, sedType = SedType.P2000)
        assertEquals(expected, actual.first().personRelasjon)
    }


    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())

        val navBruker = Fodselsnummer.fra("1201 1577847")
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_01, emptyList())
        verify(personV3Service).hentPerson(eq("12011577847"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = Fodselsnummer.fra("1201-1577847")
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_01, emptyList())
        verify(personV3Service).hentPerson(eq("12011577847"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`() {
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = Fodselsnummer.fra("1201/1577847")
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_03, emptyList())
        verify(personV3Service).hentPerson(eq("12011577847"))
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fdato`() {
        val sed = sedFromJsonFile("/buc/P10000-superenkel.json")
        val alleSediBuc = listOf(sed)
        val actual = personidentifiseringService.hentIdentifisertPerson(null, alleSediBuc, BucType.P_BUC_06, SedType.H070)
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed))
        assertEquals("1958-07-11", fdato.toString())
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fnr`() {
        val forventetFnr = "22117320034"

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(eq(forventetFnr))

        val sed1 = sedFromJsonFile("/buc/P10000-enkel.json")
        val sed2 = sedFromJsonFile("/buc/P10000-superenkel.json")
        val alleSediBuc = listOf(sed1, sed2)
        val actual = personidentifiseringService.hentIdentifisertPerson(null, alleSediBuc, BucType.P_BUC_06, SedType.H070)
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed2, sed1))

        assertEquals("1973-11-22", fdato.toString())
        assertEquals(forventetFnr, actual?.personRelasjon?.fnr!!.value)
    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
        val navBruker = "11067122781" //avdød bruker fra eux
        val gjenlevende = "09035225916"
        val bucType = BucType.P_BUC_02

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq(gjenlevende))

        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Avgått-død"))
                .`when`(personV3Service)
                .hentPerson(eq(navBruker))

        val sedListe = listOf(
                generateSED(SedType.P2100, forsikretFnr = navBruker, gjenlevFnr = gjenlevende, gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE),
                generateSED(SedType.P6000, forsikretFnr = navBruker, gjenlevFnr = gjenlevende)
        )
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(sedListe)

        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(
                Fodselsnummer.fra(navBruker), sedListe, bucType, potensiellePerson
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
        val navBruker = "11067122781" //avdød bruker fra eux
        val gjenlevende = "09035225916"
        val bucType = BucType.P_BUC_02

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq(gjenlevende))

        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Avgått-død"))
                .`when`(personV3Service)
                .hentPerson(eq(navBruker))

        val sed1 = sedFromJsonFile("/buc/P2100-PinNO.json")
        val sed2 = sedFromJsonFile("/buc/P6000-gjenlevende-NAV.json")
        val sed3 = sedFromJsonFile("/buc/P10000-person-annenperson.json")
        val alleSediBuc = listOf(sed1, sed2, sed3)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        val actual = personidentifiseringService.hentIdentifisertePersoner(
                Fodselsnummer.fra(navBruker), alleSediBuc, bucType, potensiellePerson
        )

        assertEquals(1, actual.size)
        assertEquals(gjenlevende, actual.first().personRelasjon.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, actual.first().personRelasjon.relasjon)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(actual, bucType, SedType.P10000, potensiellePerson)
        assertEquals(gjenlevende, gjenlevActual?.personRelasjon?.fnr!!.value)
        assertEquals(Relasjon.GJENLEVENDE, gjenlevActual.personRelasjon.relasjon)
    }

    @Test
    fun `Gitt en tom fnr naar fnr valideres saa svar invalid`(){
        assertFalse(PersonidentifiseringService.erFnrDnrFormat(null))
    }

    @Test
    fun `Gitt en ugyldig lengde fnr naar fnr valideres saa svar invalid`(){
        assertFalse(PersonidentifiseringService.erFnrDnrFormat("1234"))
    }

    @Test
    fun `Gitt en gyldig lengde fnr naar fnr valideres saa svar valid`(){
        assertTrue(PersonidentifiseringService.erFnrDnrFormat("12345678910"))
    }

    @Test
    fun `Gitt manglende fnr og en liste med sed som inneholder fdato som gir en gyldig fdato`(){
        val personidentifiseringService2 = PersonidentifiseringService(aktoerregisterService,
                personV3Service,
                diskresjonkodeHelper,
                FnrHelper())

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
    fun `Gitt manglende fnr og en liste med seder vil returnere en liste size 0`(){
        val sed1 = sedFromJsonFile("/buc/EmptySED.json")
        val alleSediBuc = listOf(sed1, sed1, sed1)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        val actual = personidentifiseringService.hentIdentifisertePersoner(null, alleSediBuc, BucType.P_BUC_01, potensiellePerson )
        assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt en tom liste av identifiserte personer når velger person så returner null`(){
        val alleSediBuc = emptyList<SED>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)
        assertNull(personidentifiseringService.identifisertPersonUtvelger(emptyList(), BucType.H_BUC_07, null, potensiellePerson))
    }

    @Test
    fun `Gitt en liste med en identifisert person når velger person så returner personen`(){
        val identifisertPerson = IdentifisertPerson(
                "123",
                "Testern",
                null,
                "NO",
                "010",
                PersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.FORSIKRET))
        val alleSediBuc = emptyList<SED>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        assertEquals(personidentifiseringService.identifisertPersonUtvelger(listOf(identifisertPerson), BucType.H_BUC_07, SedType.H001, potensiellePerson), identifisertPerson)
    }

    @Test
    fun `Gitt en R_BUC_02 med kun en person når personer identifiseres så returneres første person`(){
        val gjenlevende = IdentifisertPerson(
                "123",
                "Testern",
                null,
                "NO",
                "010",
                PersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.GJENLEVENDE))
        val alleSediBuc = emptyList<SED>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

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
                null,
                "NO",
                "010",
                PersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.AVDOD))

        val gjenlevende = IdentifisertPerson(
                "123",
                "Testern",
                null,
                "NO",
                "010",
                PersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.GJENLEVENDE))

        val alleSediBuc = emptyList<SED>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

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
                null,
                "NO",
                "010",
                PersonRelasjon(Fodselsnummer.fra("12345678910"), Relasjon.FORSIKRET))

        val alleSediBuc = emptyList<SED>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(listOf(forsikret, forsikret, forsikret), BucType.P_BUC_01, SedType.P2000, potensiellePerson)
        }
    }

    @Test
    fun `Gitt at det finnes tre personer når en er gjenlevende så skal kun gjenlevende returneres`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.GJENLEVENDE)

        val list = listOf(person1, person2, person3)

        val alleSediBuc = emptyList<SED>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)


        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(Relasjon.GJENLEVENDE, actual?.personRelasjon?.relasjon)
    }

    @Test
    fun `Gitt at det finnes tre personer når ingen personer er gjenlevende så skal returneres null`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(null, actual)
    }

    @Test
    fun `Gitt personidentifisering identifisere mer enn en person så kastes en runtimeexception`() {
        val person1 = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.FORSIKRET)
        val person2 = createIdentifisertPerson(Fodselsnummer.fra("2344"), Relasjon.FORSIKRET)
        val person3 = createIdentifisertPerson(Fodselsnummer.fra("4567"), Relasjon.ANNET)

        val list = listOf(person1, person2, person3)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_01, SedType.P2100, potensiellePerson)
        }
        assertThrows<RuntimeException> {
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
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        val result = personidentifiseringService.identifisertPersonUtvelger(list, BucType.R_BUC_02, SedType.P2100, potensiellePerson)

        assertEquals(person1, result)
        assertEquals(3, result?.personListe?.size)
        assertEquals(true, result?.flereEnnEnPerson())
    }

    @Test
    fun `Gitt at det ikke finnes personer på en buc så skal kun null returneres`() {
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        val actual = personidentifiseringService.identifisertPersonUtvelger(emptyList(), BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(null, actual)
    }

    @Test
    fun `hent ut person gjenlevende fra pBuc02`() {
        val avdodBrukerFnr = Fodselsnummer.fra("02116921297")
        val gjenlevendeFnr = Fodselsnummer.fra("28116925275")

        val avdodPerson = IdentifisertPerson("", "avgott Testesen", null, "NOR", "026123", PersonRelasjon(avdodBrukerFnr, Relasjon.FORSIKRET, sedType = SedType.P2100))
        val gjenlevendePerson = IdentifisertPerson("", "gjenlevende Testesen", null, "NOR", "026123", PersonRelasjon(gjenlevendeFnr, Relasjon.GJENLEVENDE, sedType = SedType.P2100))

        val identifisertePersoner = listOf(avdodPerson, gjenlevendePerson)

        val sed1 = sedFromJsonFile("/sed/P_BUC_02_P2100_Sendt.json")
        val sedListFraBuc = listOf(sed1)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(sedListFraBuc)

        doReturn(BrukerMock.createWith(fornavn = "gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq(gjenlevendeFnr!!.value))

        doReturn(BrukerMock.createWith(fornavn = "avgott"))
                .`when`(personV3Service)
                .hentPerson(eq(avdodBrukerFnr!!.value))

        val actual = personidentifiseringService.hentIdentifisertePersoner(avdodBrukerFnr, sedListFraBuc, BucType.P_BUC_02, potensiellePerson)

        assertEquals(identifisertePersoner[1], actual.single())
    }

    @Test
    fun `hent ut gjenlevende`() {
        val gjenlevende = createIdentifisertPerson(Fodselsnummer.fra("1234"), Relasjon.GJENLEVENDE)
        val avdod = createIdentifisertPerson(Fodselsnummer.fra("5678"), Relasjon.FORSIKRET)

        val list = listOf(gjenlevende, avdod)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)

        assertEquals(gjenlevende, actual)
        assertEquals(false, actual?.flereEnnEnPerson())
    }

    private fun sedFromJsonFile(file: String): SED {
        val json = javaClass.getResource(file).readText()
        return mapJsonToAny(json, typeRefs())
    }

    private fun createIdentifisertPerson(fnr: Fodselsnummer?, relasjon: Relasjon): IdentifisertPerson =
            IdentifisertPerson("", "Dummy", null, "NO", "", PersonRelasjon(fnr, relasjon))
    
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
                        bruker = listOf(Bruker(createPerson(forsikretFnr, forsikretRolle))),
                        annenperson = Bruker(person = createPerson(annenPersonFnr, annenPersonRolle)),
                        krav = navKrav?.let { Krav(type = it) }
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
            Bruker(
                    person = createPerson(it),
                    tilbakekreving = annenPersonTilbakekreving?.let { type ->
                        Tilbakekreving(status = Status(type))
                    }
            )
        }

        return SED(
                type = SedType.R005,
                nav = Nav(bruker = listOfNotNull(
                        Bruker(
                                person = createPerson(forsikretFnr),
                                tilbakekreving = forsikretTilbakekreving?.let {
                                    Tilbakekreving(status = Status(it))
                                }
                        ),
                        annenPerson
                ))
        )
    }

    private fun createPerson(fnr: String?, rolle: Rolle? = null): Person {
        return Person(
                rolle = rolle,
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
                            Person(
                                    pin = listOf(PinItem(land = "NO", identifikator = gjenlevFnr)),
                                    relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it) },
                                    rolle = rolle
                            )
                    )
            )

}
