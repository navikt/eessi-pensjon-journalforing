package no.nav.eessi.pensjon.personidentifisering.helpers

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode.SPSF
import no.nav.eessi.pensjon.personoppslag.personv3.BrukerMock
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class DiskresjonkodeHelperTest {

    @Mock
    private lateinit var personV3Service: PersonV3Service

    private lateinit var diskresjonkodeHelper: DiskresjonkodeHelper

    private lateinit var sedFnrSøk: SedFnrSøk

    @BeforeEach
    fun setup() {
        sedFnrSøk = SedFnrSøk()

        diskresjonkodeHelper = DiskresjonkodeHelper(
                personV3Service,
                sedFnrSøk
        )
    }

    @Test
    fun `Gitt ingen brukere med diskresjonskode SPSF når diskresjonskodehelper leter etter SPSF koder i alle SEDer i en BUC så returner diskresjonskode`() {
        val json = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val p2000 = mapJsonToAny(json, typeRefs<SED>())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))

        Assertions.assertEquals(null, actual)
    }

    @Test
    fun sjekkForIngenDiskresjonskodePersonIkkeFunnetException() {
        val json = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val p2000 = mapJsonToAny(json, typeRefs<SED>())

        doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doReturn(BrukerMock.createWith())
                .doReturn(BrukerMock.createWith())
                .whenever(personV3Service).hentPerson(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))
        val expected = null

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun sjekkForIngenDiskresjonskodePersonIkkeFunnetExceptionSaaFunnetMedDiskresjon() {
        val json = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val p2000 = mapJsonToAny(json, typeRefs<SED>())

        val brukerFO = BrukerMock.createWith()
        brukerFO?.diskresjonskode = Diskresjonskoder().withValue("SPFO")

        val brukerSF = BrukerMock.createWith()
        brukerSF?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

        doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doReturn(brukerFO)
                .doAnswer { throw PersonV3IkkeFunnetException("Person ikke funnet dummy") }
                .doReturn(brukerSF)
                .whenever(personV3Service).hentPerson(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))

        Assertions.assertEquals(SPSF, actual)
    }

    @Test
    fun `Gitt en bruker med diskresjonskode SPSF når diskresjonskodehelper leter etter SPSF koder i alle SEDer i en BUC`() {
        val json = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val p2000 = mapJsonToAny(json, typeRefs<SED>())

        val bruker = BrukerMock.createWith()
        bruker?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

        doReturn(bruker).whenever(personV3Service).hentPerson(any())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))

        Assertions.assertEquals(SPSF, actual)
    }
}
