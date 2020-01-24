package no.nav.eessi.pensjon.personidentifisering

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.personidentifisering.klienter.AktoerregisterKlient
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.klienter.BrukerMock
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths


@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonidentifiseringServiceTest {

    @Mock
    private lateinit var aktoerregisterKlient: AktoerregisterKlient

    @Mock
    private lateinit var personV3Klient: PersonV3Klient

    @Mock
    private lateinit var diskresjonkodeHelper: DiskresjonkodeHelper

    @Mock
    private lateinit var fnrHelper: FnrHelper

    @Mock
    private lateinit var fdatoHelper: FdatoHelper


    private lateinit var personidentifiseringService: PersonidentifiseringService

    @BeforeEach
    fun setup() {
        personidentifiseringService = PersonidentifiseringService(aktoerregisterKlient,
                personV3Klient,
                diskresjonkodeHelper,
                fnrHelper,
                fdatoHelper)

        //MOCK RESPONSES

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Klient)
                .hentPerson(ArgumentMatchers.anyString())

        //EUX - Fdatoservice (fin fdato)
        doReturn("1964-04-01")
                .`when`(fdatoHelper)
                .finnFDatoFraSeder(any())

        //EUX - FnrServide (fin pin)
        doReturn("01055012345")
                .`when`(fnrHelper)
                .getFodselsnrFraSeder(any())
    }

    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        personidentifiseringService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", bucType = BucType.P_BUC_10, sedType = SedType.P2000, navBruker = "1207 8945602"), emptyList())
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){
        personidentifiseringService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", navBruker = "1207-8945602"), listOf(""))
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`(){
        personidentifiseringService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", navBruker = "1207/8945602"), emptyList())
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
        var actual = personidentifiseringService2.identifiserPerson(SedHendelseModel(id = 16898, sektorKode = "P", bucType = BucType.P_BUC_06, rinaDokumentId = "c7bf5b349010461bb0d706deb914ba2d", navBruker = null, mottakerLand = "NO", rinaSakId = "774535"), listOf(sed))

        println(actual)
        assertEquals("1958-07-11", actual.fdato.toString())

    }

    @Test
    fun `Gitt manglende fnr så skal det slås opp fnr og fdato i seder og returnere gyldig fnr`() {
        val sed1 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-enkel.json")))
        val sed2 = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P10000-superenkel.json")))
        var actual = personidentifiseringService.identifiserPerson(SedHendelseModel(id = 16898, sektorKode = "P", bucType = BucType.P_BUC_06, rinaDokumentId = "c7bf5b349010461bb0d706deb914ba2d", navBruker = null, mottakerLand = "NO", rinaSakId = "774535"), listOf(sed2, sed1))

        println(actual)
        assertEquals("1950-05-01", actual.fdato.toString())
        assertEquals("01055012345", actual.fnr)

    }

    @Test
    fun `Gitt en tom fnr naar fnr valideres saa svar invalid`(){
        assertFalse(personidentifiseringService.isFnrValid(null))
    }

    @Test
    fun `Gitt en ugyldig lengde fnr naar fnr valideres saa svar invalid`(){
        assertFalse(personidentifiseringService.isFnrValid("1234"))
    }

    @Test
    fun `Gitt en gyldig lengde fnr naar fnr valideres saa svar valid`(){
        assertTrue(personidentifiseringService.isFnrValid("12345678910"))
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
        var actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed))

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

        var actual = personidentifiseringService2.hentFodselsDato(null, listOf(sed2, sed1))
        assertEquals("1980-01-01", actual.toString())
    }

}