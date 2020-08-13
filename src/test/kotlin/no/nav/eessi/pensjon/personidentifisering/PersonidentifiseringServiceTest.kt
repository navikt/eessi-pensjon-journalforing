package no.nav.eessi.pensjon.personidentifisering

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.aktoerregister.Result
import no.nav.eessi.pensjon.personoppslag.personv3.BrukerMock
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions.*
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
        whenever(aktoerregisterService.hentGjeldendeIdentFraGruppe(eq(IdentGruppe.AktoerId), any<NorskIdent>()))
                .thenReturn(Result.NotFound("Tests don't care about aktoerId"))

        personidentifiseringService = PersonidentifiseringService(aktoerregisterService,
                personV3Service,
                diskresjonkodeHelper,
                FnrHelper(),
                fdatoHelper)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon gjenlevende så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("05127921999"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2100-PinNO.json")))
        val actual = personidentifiseringService.hentIdentifisertPerson (null, listOf(sed), BucType.P_BUC_02 )
        val expected = PersonRelasjon("05127921999", Relasjon.GJENLEVENDE, YtelseType.GJENLEV )
        assertEquals(expected, actual?.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon avdod så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("28115518943"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R005-avdod-enke-NAV.json")))
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, listOf(sed), BucType.R_BUC_02 )
        val expected = PersonRelasjon("28115518943", Relasjon.AVDOD)

        val actutalRelasjon = actual.map { it.personRelasjon }.toList()
        assertTrue(actutalRelasjon.contains(expected))
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon forsikret så skal det identifiseres en person`() {
        whenever(personV3Service.hentPerson(eq("67097097000"))).thenReturn(BrukerMock.createWith(landkoder = true))
        whenever(diskresjonkodeHelper.hentDiskresjonskode(any())).thenReturn(null)

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2000-NAV.json")))
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, listOf(sed), BucType.P_BUC_01 )
        val expected = PersonRelasjon("67097097000", Relasjon.FORSIKRET)
        assertEquals(expected, actual.first().personRelasjon)
    }


    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())

        val navBruker = "1207 8945602"
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_01)
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = "1207-8945602"
        personidentifiseringService.hentIdentifisertePersoner(navBruker, listOf(""), BucType.P_BUC_01 )
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`(){
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Service)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = "1207/8945602"
        personidentifiseringService.hentIdentifisertePersoner(navBruker, emptyList(), BucType.P_BUC_03 )
        verify(personV3Service).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fdato`() {
        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val actual = personidentifiseringService.hentIdentifisertPerson(null, listOf(sed), BucType.P_BUC_06 )
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
        val actual = personidentifiseringService.hentIdentifisertPerson(null, listOf(sed2, sed1), BucType.P_BUC_06)
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

        val actual = personidentifiseringService.hentIdentifisertePersoner(navBruker, listOf(sed1, sed2), bucType)
        assertEquals(2, actual.size)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(actual, bucType)
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

        val actual = personidentifiseringService.hentIdentifisertePersoner(navBruker, listOf(sed3, sed1, sed2), bucType)
        assertEquals(2, actual.size)

        val gjenlevActual = personidentifiseringService.identifisertPersonUtvelger(actual, bucType)
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
        val actual = personidentifiseringService.hentIdentifisertePersoner(null, listOf(sed1, sed1, sed1, sed1), BucType.P_BUC_01 )
        assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt en tom liste av identifiserte personer når velger person så returner null`(){
       assertNull(personidentifiseringService.identifisertPersonUtvelger(emptyList(), BucType.H_BUC_07))
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
        assertEquals(personidentifiseringService.identifisertPersonUtvelger(listOf(identifisertPerson), BucType.H_BUC_07), identifisertPerson)
    }

    @Test
    fun `Gitt en liste med to hovedpersoner så kast RuntimeException`(){
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
        assertThrows<RuntimeException>{personidentifiseringService.identifisertPersonUtvelger(listOf(avdod, gjenlevende), BucType.R_BUC_02)}
    }

    @Test
    fun `Gitt en liste med flere avdøde på R_BUC_02 så vreturnerer vi RuntimeException`(){
        val avdod = IdentifisertPerson(
                "123",
                "Testern",
                null,
                "NO",
                "010",
                PersonRelasjon("12345678910", Relasjon.AVDOD))
        assertThrows<RuntimeException>{personidentifiseringService.identifisertPersonUtvelger(listOf(avdod, avdod, avdod, avdod, avdod), BucType.R_BUC_02)}
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

        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(listOf(forsikret, forsikret, forsikret), BucType.P_BUC_01)
        }
    }

    @Test
    fun `Gitt at det finnes tre personer når en er gjenlevende så skal kun gjenlevende returneres`() {

        val person1 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("4567", Relasjon.GJENLEVENDE))

        val list = listOf(person1, person2, person3)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02)
        assertEquals(Relasjon.GJENLEVENDE, actual?.personRelasjon?.relasjon)
    }

    @Test
    fun `Gitt at det finnes tre personer når ingen personer er gjenlevende så skal returneres null`() {

        val person1 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("4567", Relasjon.ANNET))

        val list = listOf(person1, person2, person3)

        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02)
        assertEquals(null, actual)
    }

    @Test
    fun `Gitt at det finnes tre personer når det er en buc så skal det kastes en runtimeexception`() {

        val person1 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("1234", Relasjon.FORSIKRET))
        val person2 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("2344", Relasjon.FORSIKRET))
        val person3 = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("4567", Relasjon.ANNET))

        val list = listOf(person1, person2, person3)

        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_01)
        }
        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_03)
        }
        assertThrows<RuntimeException> {
            personidentifiseringService.identifisertPersonUtvelger(list, BucType.R_BUC_02)
        }
    }


    @Test
    fun `Gitt at det ikke finnes personer på en buc så skal kun null returneres`() {

        val list = listOf<IdentifisertPerson>()
        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02)
        assertEquals(null, actual)
    }

    @Test
    fun `hent ut person gjenlevende fra pBuc02`() {

        val avdodBrukerFnr = "02116921297"
        val gjenlevendeFnr = "28116925275"

        val avdodPerson = IdentifisertPerson("","avgott Testesen", null,"NOR", "026123", PersonRelasjon(avdodBrukerFnr, Relasjon.FORSIKRET))
        val gjenlevendePerson = IdentifisertPerson("","gjenlevende Testesen", null,"NOR", "026123", PersonRelasjon(gjenlevendeFnr, Relasjon.GJENLEVENDE))

        val identifisertePersoner = listOf(avdodPerson, gjenlevendePerson)

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_02_P2100_Sendt.json")))
        val sedListFraBuc = listOf(sed1)


        doReturn(BrukerMock.createWith(fornavn = "gjenlevende"))
                .`when`(personV3Service)
                .hentPerson(eq(gjenlevendeFnr))

        doReturn(BrukerMock.createWith(fornavn = "avgott"))
                .`when`(personV3Service)
                .hentPerson(eq(avdodBrukerFnr))

        val actual = personidentifiseringService.hentIdentifisertePersoner(avdodBrukerFnr, sedListFraBuc, BucType.P_BUC_02)

        assertEquals(identifisertePersoner, actual)
    }

    @Test
    fun `hent ut gjenlevende`(){


        val gjenlevende = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("1234", Relasjon.GJENLEVENDE))
        val avdod = IdentifisertPerson("","Dummy", "","NO", "", PersonRelasjon("5678", Relasjon.FORSIKRET))

        val list = listOf<IdentifisertPerson>(gjenlevende, avdod)




        val actual = personidentifiseringService.identifisertPersonUtvelger(list, BucType.P_BUC_02)

        assertEquals(gjenlevende, actual)
    }




}
