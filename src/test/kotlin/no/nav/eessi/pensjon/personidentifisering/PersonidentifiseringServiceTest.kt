package no.nav.eessi.pensjon.personidentifisering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
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
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths


@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonidentifiseringServiceTest {

    @Mock
    private lateinit var aktoerregisterService: AktoerregisterService

    @Mock
    private lateinit var personV3Service: PersonV3Service

    @Mock
    private lateinit var diskresjonkodeHelper: DiskresjonkodeHelper

    @Spy
    private lateinit var fdatoHelper: FdatoHelper

    private lateinit var personidentifiseringService: PersonidentifiseringService

    @BeforeEach
    fun setup() {
        whenever(aktoerregisterService.hentGjeldendeIdent(eq(IdentGruppe.AktoerId), any<NorskIdent>()))
                .thenReturn(null)

        personidentifiseringService = PersonidentifiseringService(aktoerregisterService,
                personV3Service,
                diskresjonkodeHelper,
                FnrHelper(),
                fdatoHelper)
    }

    @Test
    fun `Gitt en H070 der det finnes en p6000 med gjenlevende i samme buc så identifiser forsikret person`() {
        whenever(personV3Service.hentPerson(eq("05127921999"))).thenReturn(BrukerMock.createWith(landkoder = true))
        whenever(personV3Service.hentPerson(eq("12078945602"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val p6000 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P6000-gjenlevende-NAV.json")))
        val h070 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/H070-NAV.json")))
        val actual = personidentifiseringService.hentIdentifisertPerson (null, listOf(p6000, h070), BucType.P_BUC_05, SedType.H070 )
        val expected = PersonRelasjon("12078945602", Relasjon.FORSIKRET, null, sedType = SedType.H070)
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt en Sed som inneholder gjenlevende som ikke er en del av samlingen av Seds som er forsikret, dette er feks H070, H120, H121 så identifiseres en gjenlevende`() {
        whenever(personV3Service.hentPerson(eq("05127921999"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val p6000 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P6000-gjenlevende-NAV.json")))
        val actual = personidentifiseringService.hentIdentifisertPerson (null, listOf(p6000), BucType.P_BUC_05, SedType.P6000 )
        val expected = PersonRelasjon("05127921999", Relasjon.GJENLEVENDE, null, SedType.P6000)
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon gjenlevende så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("05127921999"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2100-PinNO.json")))
        val actual = personidentifiseringService.hentIdentifisertPerson (null, listOf(sed), BucType.P_BUC_02, SedType.H070 )
        val expected = PersonRelasjon("05127921999", Relasjon.GJENLEVENDE, YtelseType.GJENLEV , sedType = SedType.P2100)
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon avdod så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("28115518943"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R005-avdod-enke-NAV.json")))
        val alleSediBuc = listOf(sed)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, listOf(sed), BucType.R_BUC_02, potensiellePerson)
        val expected = PersonRelasjon("28115518943", Relasjon.AVDOD, sedType = SedType.R005)

        val actutalRelasjon = actual.map { it.personRelasjon }.toList()
        assertTrue(actutalRelasjon.contains(expected))
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon forsikret så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("67097097000"))).thenReturn(BrukerMock.createWith(landkoder = true))
        whenever(diskresjonkodeHelper.hentDiskresjonskode(any())).thenReturn(null)

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2000-NAV.json")))
        val alleSediBuc = listOf(sed)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, alleSediBuc, BucType.P_BUC_01, potensiellePerson )
        val expected = PersonRelasjon("67097097000", Relasjon.FORSIKRET, sedType = SedType.P2000)
        assertEquals(expected, actual.first().personRelasjon)
    }


    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())

        val navBruker = "1207 8945602"
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_01, emptyList())
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = "1207-8945602"
        personidentifiseringService.hentIdentifisertePersoner(navBruker, listOf(""), BucType.P_BUC_01, emptyList())
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`(){
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = "1207/8945602"
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_03, emptyList() )
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fdato`() {
        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val alleSediBuc = listOf(sed)
        val actual = personidentifiseringService.hentIdentifisertPerson(null, alleSediBuc, BucType.P_BUC_06, SedType.H070)
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed))
        assertEquals("1958-07-11", fdato.toString())
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fnr`() {

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(eq("28064843062"))

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-enkel.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val alleSediBuc = listOf(sed1, sed2)
        val actual = personidentifiseringService.hentIdentifisertPerson(null, alleSediBuc, BucType.P_BUC_06, SedType.H070)
        val fdato = personidentifiseringService.hentFodselsDato(actual, listOf(sed2, sed1))

        assertEquals("1948-06-28", fdato.toString())
        assertEquals("28064843062", actual?.personRelasjon?.fnr)

    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
        val navBruker = "28127822044" //avdød bruker fra eux
        val gjenlevende = "05127921999"
        val bucType = BucType.P_BUC_02

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq("05127921999"))

        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Avgått-død"))
                .`when`(personV3Service)
                .hentPerson(eq("28127822044"))

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2100-PinNO.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P6000-gjenlevende-NAV.json")))
        val alleSediBuc = listOf(sed1, sed2)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        val actual = personidentifiseringService.hentIdentifisertePersoner(navBruker, alleSediBuc, bucType,potensiellePerson )
        assertEquals(2, actual.size)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(actual, bucType, SedType.P6000, potensiellePerson)
        assertEquals(gjenlevende, gjenlevActual?.personRelasjon?.fnr)
        assertEquals(Relasjon.GJENLEVENDE, gjenlevActual?.personRelasjon?.relasjon)

    }

    @Test
    fun `Gitt fnr på navbruker på en P_BUC_02 med P2100 og P10000 så skal det slås opp fnr og fdato i seder og returnere gyldig gjenlevendefnr`() {
        //EUX - FnrServide (fin pin)
        val navBruker = "28127822044" //avdød bruker fra eux
        val gjenlevende = "05127921999"
        val bucType = BucType.P_BUC_02

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq("05127921999"))

        doReturn(BrukerMock.createWith(landkoder = true, fornavn = "Avgått-død"))
                .`when`(personV3Service)
                .hentPerson(eq("28127822044"))

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2100-PinNO.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P6000-gjenlevende-NAV.json")))
        val sed3 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-person-annenperson.json")))
        val alleSediBuc = listOf(sed1, sed2, sed3)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        val actual = personidentifiseringService.hentIdentifisertePersoner(navBruker,alleSediBuc, bucType, potensiellePerson)
        assertEquals(2, actual.size)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(actual, bucType, SedType.P10000, potensiellePerson)
        assertEquals(gjenlevende, gjenlevActual?.personRelasjon?.fnr)
        assertEquals(Relasjon.GJENLEVENDE, gjenlevActual?.personRelasjon?.relasjon)

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
    fun `Gitt en ugyldig lengde fnr naar fnr valideres saa svar valid`(){
        assertFalse(PersonidentifiseringService.erFnrDnrFormat(PersonidentifiseringService.trimFnrString("52015410191/ 22435184")))
    }

    @Test
    fun `Gitt forskjellige typer ugyldig fnr så svar med valid fnr`(){
        assertFalse(PersonidentifiseringService.erFnrDnrFormat(PersonidentifiseringService.trimFnrString("520154aieygr")))
        assertTrue(PersonidentifiseringService.erFnrDnrFormat(PersonidentifiseringService.trimFnrString("5201541-224-3")))
        assertTrue(PersonidentifiseringService.erFnrDnrFormat(PersonidentifiseringService.trimFnrString("52 01 541 0191- ")))
        assertTrue(PersonidentifiseringService.erFnrDnrFormat(PersonidentifiseringService.trimFnrString("3520&&&1/ 22-43-23-")))
        assertTrue(PersonidentifiseringService.erFnrDnrFormat(PersonidentifiseringService.trimFnrString("551073 49331")))
    }

    @Test
    fun `Gitt manglende fnr og en liste med sed som inneholder fdato som gir en gyldig fdato`(){
        val fdatoHelper2 = FdatoHelper()

        val personidentifiseringService2 = PersonidentifiseringService(aktoerregisterService,
                personV3Service,
                diskresjonkodeHelper,
                FnrHelper(),
                fdatoHelper2)

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed))

        assertEquals("1958-07-11", actual.toString())
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder som inneholder fdato som gir en gyldig fdato`(){
        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-enkel.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2000-NAV.json")))

        val actual = personidentifiseringService.hentFodselsDato(null, listOf(sed2, sed1))
        assertEquals("1980-01-01", actual.toString())
    }


    @Test
    fun `Gitt manglende fnr og tom liste med seder kaster RunTimeException`(){
        doReturn(null)
                .`when`(fdatoHelper)
                .finnEnFdatoFraSEDer(any())

        assertThrows<RuntimeException> {
            personidentifiseringService.hentFodselsDato(null, emptyList())
        }
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder vil returnere en liste size 0`(){
        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/EmptySED.json")))
        val alleSediBuc = listOf(sed1, sed1, sed1)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        val actual = personidentifiseringService.hentIdentifisertePersoner(null, alleSediBuc, BucType.P_BUC_01, potensiellePerson )
        assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt en tom liste av identifiserte personer når velger person så returner null`(){
        val alleSediBuc = emptyList<String>()
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
                PersonRelasjon("12345678910", Relasjon.FORSIKRET))
        val alleSediBuc = emptyList<String>()
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
                PersonRelasjon("12345678910", Relasjon.GJENLEVENDE))
        val alleSediBuc = emptyList<String>()
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
                PersonRelasjon("12345678910", Relasjon.AVDOD))

        val gjenlevende = IdentifisertPerson(
                "123",
                "Testern",
                null,
                "NO",
                "010",
                PersonRelasjon("12345678910", Relasjon.GJENLEVENDE))

        val alleSediBuc = emptyList<String>()
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
                PersonRelasjon("12345678910", Relasjon.FORSIKRET))

        val alleSediBuc = emptyList<String>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(listOf(forsikret, forsikret, forsikret), BucType.P_BUC_01, SedType.P2000, potensiellePerson)
        }
    }

    @Test
    fun `Gitt at det finnes tre personer når en er gjenlevende så skal kun gjenlevende returneres`() {

        val person1 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("4567", Relasjon.GJENLEVENDE))

        val list = listOf(person1, person2, person3)

        val alleSediBuc = emptyList<String>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)


        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(Relasjon.GJENLEVENDE, actual?.personRelasjon?.relasjon)
    }

    @Test
    fun `Gitt at det finnes tre personer når ingen personer er gjenlevende så skal returneres null`() {

        val person1 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("4567", Relasjon.ANNET))

        val list = listOf(person1, person2, person3)
        val alleSediBuc = emptyList<String>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)


        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(null, actual)
    }

    @Test
    fun `Gitt personidentifisering identifisere mer enn en person så kastes en runtimeexception`() {

        val person1 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("4567", Relasjon.ANNET))

        val list = listOf(person1, person2, person3)
        val alleSediBuc = emptyList<String>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

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
        val person1 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("4567", Relasjon.ANNET))

        val list = listOf(person1, person2, person3)
        val alleSediBuc = emptyList<String>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(alleSediBuc)

        val result = personidentifiseringService.identifisertPersonUtvelger(list, BucType.R_BUC_02, SedType.P2100, potensiellePerson )

        assertEquals(person1, result)
        assertEquals(3, result?.personListe?.size)
        assertEquals(true , result?.flereEnnEnPerson())

    }




    @Test
    fun `Gitt at det ikke finnes personer på en buc så skal kun null returneres`() {

        val list = listOf<IdentifisertPerson>()
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)
        assertEquals(null, actual)
    }

    @Test
    fun `hent ut person gjenlevende fra pBuc02`() {

        val avdodBrukerFnr = "02116921297"
        val gjenlevendeFnr = "28116925275"

        val avdodPerson = IdentifisertPerson("","avgott Testesen", null,"NOR", "026123", PersonRelasjon(avdodBrukerFnr, Relasjon.FORSIKRET, sedType = SedType.P2100))
        val gjenlevendePerson = IdentifisertPerson("","gjenlevende Testesen", null,"NOR", "026123", PersonRelasjon(gjenlevendeFnr, Relasjon.GJENLEVENDE, sedType = SedType.P2100))

        val identifisertePersoner = listOf(avdodPerson, gjenlevendePerson)

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100_Sendt.json")))
        val sedListFraBuc = listOf(sed1)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(sedListFraBuc)

        doReturn(BrukerMock.createWith(fornavn = "gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq(gjenlevendeFnr))

        doReturn(BrukerMock.createWith(fornavn = "avgott"))
                .`when`(personV3Service)
                .hentPerson(eq(avdodBrukerFnr))

        val actual = personidentifiseringService.hentIdentifisertePersoner(avdodBrukerFnr, sedListFraBuc, BucType.P_BUC_02, potensiellePerson)

        assertEquals(identifisertePersoner, actual)
    }

    @Test
    fun `hent ut gjenlevende`(){


        val gjenlevende = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("1234", Relasjon.GJENLEVENDE))
        val avdod = IdentifisertPerson("","Dummy", null,"NO", "", PersonRelasjon("5678", Relasjon.FORSIKRET))

        val list = listOf<IdentifisertPerson>(gjenlevende, avdod)
        val potensiellePerson = personidentifiseringService.potensiellePersonRelasjonfraSed(emptyList())

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02, SedType.P2100, potensiellePerson)

        assertEquals(gjenlevende, actual)
        assertEquals(false, actual?.flereEnnEnPerson())

    }




}
