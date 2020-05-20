package no.nav.eessi.pensjon.personidentifisering

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.klienter.AktoerregisterKlient
import no.nav.eessi.pensjon.personidentifisering.klienter.BrukerMock
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
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
import java.time.LocalDate


@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonidentifiseringServiceTest {

    @Mock
    private lateinit var aktoerregisterKlient: AktoerregisterKlient

    @Mock
    private lateinit var personV3Klient: PersonV3Klient

    @Mock
    private lateinit var diskresjonkodeHelper: DiskresjonkodeHelper

    @Spy
    private lateinit var fdatoHelper: FdatoHelper

    @Mock
    private lateinit var fnrHelper: FnrHelper

    private lateinit var personidentifiseringService: PersonidentifiseringService

    @BeforeEach
    fun setup() {
        //fnrHelper = FnrHelper(personV3Klient)
        personidentifiseringService = PersonidentifiseringService(aktoerregisterKlient,
                personV3Klient,
                diskresjonkodeHelper,
                fnrHelper,
                fdatoHelper)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon gjenlevende så skal det opprettes en person`() {
        doReturn( setOf(PersonRelasjon("01055012346", Relasjon.GJENLEVENDE)))
                .`when`(fnrHelper)
                .getPotensielleFnrFraSeder(any())

        //PERSONV3 - HENT PERSON´
        whenever(personV3Klient.hentPerson(eq("01055012346"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val actual = personidentifiseringService.identifiserPerson( null,  listOf())
        val expected = PersonRelasjon("01055012346", Relasjon.GJENLEVENDE)
        assertEquals(expected, actual.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon avdod så skal det opprettes en person`() {
        doReturn( setOf(PersonRelasjon("01055012666", Relasjon.AVDOD)))
                .`when`(fnrHelper)
                .getPotensielleFnrFraSeder(any())

        //PERSONV3 - HENT PERSON´
        whenever(personV3Klient.hentPerson(eq("01055012666"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val actual = personidentifiseringService.identifiserPerson( null,  listOf())
        val expected = PersonRelasjon("01055012666", Relasjon.AVDOD)
        assertEquals(expected, actual.personRelasjon)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon forsikret så skal det opprettes en person`() {
        doReturn( setOf(PersonRelasjon("01055012345", Relasjon.FORSIKRET)))
                .`when`(fnrHelper)
                .getPotensielleFnrFraSeder(any())

        //PERSONV3 - HENT PERSON´
        whenever(personV3Klient.hentPerson(eq("01055012345"))).thenReturn(BrukerMock.createWith(landkoder = true))

        val actual = personidentifiseringService.identifiserPerson( null,  listOf())
        val expected = PersonRelasjon("01055012345", Relasjon.FORSIKRET)
        assertEquals(expected, actual.personRelasjon)
    }


    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Klient)
                .hentPerson(ArgumentMatchers.anyString())

        val navBruker = "1207 8945602"
        personidentifiseringService.identifiserPerson(navBruker, emptyList())
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){

        //EUX - Fdatoservice (fin fdato)
        doReturn(LocalDate.of(1964, 4,1))
                .`when`(fdatoHelper)
                .finnEnFdatoFraSEDer(any())

        val navBruker = "1207-8945602"
        personidentifiseringService.identifiserPerson(navBruker, listOf(""))
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`(){
        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Klient)
                .hentPerson(ArgumentMatchers.anyString())
        val navBruker = "1207/8945602"
        personidentifiseringService.identifiserPerson(navBruker, emptyList())
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fdato`() {
        val fdatoHelper2 = FdatoHelper()
        val fnrHelper2 = FnrHelper()

        val personidentifiseringService2 = PersonidentifiseringService(aktoerregisterKlient,
                personV3Klient,
                diskresjonkodeHelper,
                fnrHelper2,
                fdatoHelper2)

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val navBruker = null
        val actual = personidentifiseringService2.identifiserPerson(navBruker, listOf(sed))

        println(actual)
        assertEquals("1958-07-11", actual.fdato.toString())

    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fnr`() {
        //EUX - FnrServide (fin pin)
        doReturn( setOf(PersonRelasjon("01055012345",Relasjon.FORSIKRET)))
                .`when`(fnrHelper)
                .getPotensielleFnrFraSeder(any())
        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Klient)
                .hentPerson(eq("01055012345"))

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-enkel.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val navBruker = null
        val actual = personidentifiseringService.identifiserPerson(navBruker, listOf(sed2, sed1))

        println(actual)
        assertEquals("1950-05-01", actual.fdato.toString())
        assertEquals("01055012345", actual.personRelasjon?.fnr)

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

        val personidentifiseringService2 = PersonidentifiseringService(aktoerregisterKlient,
                personV3Klient,
                diskresjonkodeHelper,
                fnrHelper,
                fdatoHelper2)

        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        val actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed))

        assertEquals("1958-07-11", actual.toString())
    }

    @Test
    fun `Gitt manglende fnr og en liste med seder som inneholder fdato som gir en gyldig fdato`(){
        val fdatoHelper2 = FdatoHelper()

        val personidentifiseringService2 = PersonidentifiseringService(aktoerregisterKlient,
                personV3Klient,
                diskresjonkodeHelper,
                fnrHelper,
                fdatoHelper2)

        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-enkel.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2000-NAV.json")))

        val actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed2, sed1))
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
    fun `Gitt manglende fnr og en liste med seder som IKKE inneholder fdato kaster RuntimeException`(){
        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/EmptySED.json")))
        assertThrows<RuntimeException> {
            personidentifiseringService.identifiserPerson(null, listOf(sed1))
        }
    }

}
