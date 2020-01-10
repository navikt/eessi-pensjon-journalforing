package no.nav.eessi.pensjon.personidentifisering

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.personidentifisering.klienter.AktoerregisterKlient
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness


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

    @Mock
    private lateinit var euxService: EuxService

    @Mock
    private lateinit var fagmodulService: FagmodulService


    private lateinit var personidentifiseringService: PersonidentifiseringService

    @BeforeEach
    fun setup() {

        personidentifiseringService = PersonidentifiseringService(aktoerregisterKlient,
                personV3Klient,
                diskresjonkodeHelper,
                fnrHelper,
                fdatoHelper,
                fagmodulService,
                euxService)

        //MOCK RESPONSES

        //PERSONV3 - HENT PERSON
        doReturn(BrukerMock.createWith(landkoder = true))
                .`when`(personV3Klient)
                .hentPerson(ArgumentMatchers.anyString())

        //EUX - HENT FODSELSDATO
        doReturn("1964-04-19")
                .`when`(euxService)
                .hentFodselsDatoFraSed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())

        //EUX - Fdatoservice (fin fdato)
        doReturn("1964-04-01")
                .`when`(fdatoHelper)
                .finnFDatoFraSeder(any())

        //EUX - FnrServide (fin pin)
        doReturn("01055012345")
                .`when`(fnrHelper)
                .getFodselsnrFraSeder(any())

        //EUX - HENT SED DOKUMENT
        doReturn("MOCK DOCUMENTS")
                .`when`(euxService)
                .hentSedDokumenter(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())

    }

    @Test
    fun `Gitt et gyldig fnr med mellomrom når identifiser person så hent person uten mellomrom`(){
        personidentifiseringService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", bucType = BucType.P_BUC_10, sedType = SedType.P2000, navBruker = "1207 8945602"))
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med bindestrek når identifiser person så hent person uten bindestrek`(){
        personidentifiseringService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", navBruker = "1207-8945602"))
        verify(personV3Klient).hentPerson(eq("12078945602"))
    }

    @Test
    fun `Gitt et gyldig fnr med slash når identifiser person så hent person uten slash`(){
        personidentifiseringService.identifiserPerson(SedHendelseModel(sektorKode = "P", rinaDokumentId = "b12e06dda2c7474b9998c7139c841646", rinaSakId = "147729", navBruker = "1207/8945602"))
        verify(personV3Klient).hentPerson(eq("12078945602"))
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
}