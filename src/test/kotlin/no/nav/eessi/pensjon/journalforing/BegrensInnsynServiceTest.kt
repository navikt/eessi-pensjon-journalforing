package no.nav.eessi.pensjon.journalforing

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.sed.SedFnrSøk
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
import no.nav.eessi.pensjon.services.personv3.BrukerMock
import no.nav.eessi.pensjon.services.personv3.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class BegrensInnsynServiceTest {

    @Mock
    private lateinit var euxService: EuxService

    @Mock
    private lateinit var fagmodulService: FagmodulService

    @Mock
    private lateinit var personV3Service: PersonV3Service

    private lateinit var begrensInnsynService: BegrensInnsynService

    private lateinit var sedFnrSøk: SedFnrSøk

    @BeforeEach
    fun setup() {
        sedFnrSøk = SedFnrSøk()

        begrensInnsynService = BegrensInnsynService(
                euxService,
                fagmodulService,
                personV3Service,
                sedFnrSøk
        )
    }

    @Test
    fun sjekkeUtJacksonMapper() {
        val json = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val expected = listOf<String>("44cb68f89a2f4e748934fb4722721018")
        val actual = begrensInnsynService.hentSedDocumentsIds(json)

        Assertions.assertEquals(expected, actual)

    }

    @Test
    fun sjekkForIngenDiskresjonskode() {

        val json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val p2000Json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))
        val allSedsJson = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val hendelse = SedHendelseModel.fromJson(json)

        doReturn(p2000Json).whenever(euxService).hentSed(any(), any())

        doReturn(BrukerMock.createWith()).whenever(personV3Service).hentPerson(any())

        doReturn(allSedsJson).whenever(fagmodulService).hentAlleDokumenterFraRinaSak(any())

        val actual = begrensInnsynService.begrensInnsyn(hendelse)
        val expected = null

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun sjekkForIngenDiskresjonskodePersonIkkeFunnetException() {

        val json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val p2000Json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))
        val allSedsJson = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val hendelse = SedHendelseModel.fromJson(json)

        doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doReturn(BrukerMock.createWith())
                .doReturn(BrukerMock.createWith())
                .whenever(personV3Service).hentPerson(any())


        doReturn(p2000Json).whenever(euxService).hentSed(any(), any())
        doReturn(allSedsJson).whenever(fagmodulService).hentAlleDokumenterFraRinaSak(any())

        val actual = begrensInnsynService.begrensInnsyn(hendelse)
        val expected = null

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun sjekkForIngenDiskresjonskodePersonIkkeFunnetExceptionSaaFunnetMedDiskresjon() {

        val json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val p2000Json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))
        val allSedsJson = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val hendelse = SedHendelseModel.fromJson(json)

        val brukerFO = BrukerMock.createWith()
        brukerFO?.diskresjonskode = Diskresjonskoder().withValue("SPFO")

        val brukerSF = BrukerMock.createWith()
        brukerSF?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

        doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }

                .doReturn(brukerFO)
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doReturn(BrukerMock.createWith())
                .doReturn(brukerSF)
                .whenever(personV3Service).hentPerson(any())

        doReturn(p2000Json).whenever(euxService).hentSed(any(), any())
        doReturn(allSedsJson).whenever(fagmodulService).hentAlleDokumenterFraRinaSak(any())

        val actual = begrensInnsynService.begrensInnsyn(hendelse)
        val expected = Diskresjonskode.SPSF

        Assertions.assertEquals(expected, actual)
    }



    @Test
    fun sjekkForDiskresjonskodeSPSFFunnet() {

        val json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val p2000Json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))
        val allSedsJson = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val hendelse = SedHendelseModel.fromJson(json)

        doReturn(p2000Json).whenever(euxService).hentSed(any(), any())

        val bruker = BrukerMock.createWith()
        bruker?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).

            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).
            doReturn(bruker).whenever(personV3Service).hentPerson(any())

        doReturn(allSedsJson).whenever(fagmodulService).hentAlleDokumenterFraRinaSak(any())

        val actual = begrensInnsynService.begrensInnsyn(hendelse)
        val expected = Diskresjonskode.SPSF

        Assertions.assertEquals(expected, actual)


    }



}