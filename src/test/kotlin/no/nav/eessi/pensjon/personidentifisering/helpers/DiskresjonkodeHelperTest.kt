package no.nav.eessi.pensjon.personidentifisering.helpers

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.personidentifisering.klienter.BrukerMock
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class DiskresjonkodeHelperTest {

    @Mock
    private lateinit var euxKlient: EuxKlient

    @Mock
    private lateinit var fagmodulKlient: FagmodulKlient

    @Mock
    private lateinit var personV3Klient: PersonV3Klient

    private lateinit var diskresjonkodeHelper: DiskresjonkodeHelper

    private lateinit var sedFnrSøk: SedFnrSøk

    @BeforeEach
    fun setup() {
        sedFnrSøk = SedFnrSøk()

        diskresjonkodeHelper = DiskresjonkodeHelper(
                euxKlient,
                fagmodulKlient,
                personV3Klient,
                sedFnrSøk
        )
    }

    @Test
    fun sjekkeUtJacksonMapper() {
        val json = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val expected = listOf("44cb68f89a2f4e748934fb4722721018")
        val actual = diskresjonkodeHelper.hentSedDocumentsIds(json)

        Assertions.assertEquals(expected, actual)

    }

    @Test
    fun sjekkForIngenDiskresjonskode() {

        val json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val p2000Json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))
        val allSedsJson = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val hendelse = SedHendelseModel.fromJson(json)

        doReturn(p2000Json).whenever(euxKlient).hentSed(any(), any())

        doReturn(BrukerMock.createWith()).whenever(personV3Klient).hentPerson(any())

        doReturn(allSedsJson).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(hendelse)
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
                .whenever(personV3Klient).hentPerson(any())


        doReturn(p2000Json).whenever(euxKlient).hentSed(any(), any())
        doReturn(allSedsJson).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(hendelse)
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
                .whenever(personV3Klient).hentPerson(any())

        doReturn(p2000Json).whenever(euxKlient).hentSed(any(), any())
        doReturn(allSedsJson).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(hendelse)
        val expected = Diskresjonskode.SPSF

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun sjekkForDiskresjonskodeSPSFFunnet() {

        val json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P_BUC_01_P2000.json")))
        val p2000Json = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P2000-NAV.json")))
        val allSedsJson = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val hendelse = SedHendelseModel.fromJson(json)

        doReturn(p2000Json).whenever(euxKlient).hentSed(any(), any())

        val bruker = BrukerMock.createWith()
        bruker?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).

            doReturn(BrukerMock.createWith()).
            doReturn(BrukerMock.createWith()).
            doReturn(bruker).whenever(personV3Klient).hentPerson(any())

        doReturn(allSedsJson).whenever(fagmodulKlient).hentAlleDokumenter(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(hendelse)
        val expected = Diskresjonskode.SPSF

        Assertions.assertEquals(expected, actual)
    }
}