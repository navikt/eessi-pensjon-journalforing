package no.nav.eessi.pensjon.personidentifisering.helpers

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode.SPSF
import no.nav.eessi.pensjon.personoppslag.personv3.BrukerMock
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class DiskresjonkodeHelperTest {

    private val personV3Service = mockk<PersonV3Service>()

    private val diskresjonkodeHelper = DiskresjonkodeHelper(personV3Service)

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

        every { personV3Service.hentPerson("") }
                .throws(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThenThrows(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThenThrows(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThenThrows(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThenThrows(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThenThrows(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThen(BrukerMock.createWith())
                .andThen(BrukerMock.createWith())

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))

        Assertions.assertNull(actual)
    }

    @Test
    fun sjekkForIngenDiskresjonskodePersonIkkeFunnetExceptionSaaFunnetMedDiskresjon() {
        val json = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val p2000 = mapJsonToAny(json, typeRefs<SED>())

        val brukerFO = BrukerMock.createWith()
        brukerFO?.diskresjonskode = Diskresjonskoder().withValue("SPFO")

        val brukerSF = BrukerMock.createWith()
        brukerSF?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

        every { personV3Service.hentPerson(any()) }
                .throws(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThen(brukerFO)
                .andThenThrows(PersonV3IkkeFunnetException("Person ikke funnet dummy"))
                .andThen(brukerSF)

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))

        Assertions.assertEquals(SPSF, actual)
    }

    @Test
    fun `Gitt en bruker med diskresjonskode SPSF når diskresjonskodehelper leter etter SPSF koder i alle SEDer i en BUC`() {
        val json = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val p2000 = mapJsonToAny(json, typeRefs<SED>())

        val bruker = BrukerMock.createWith()
        bruker?.diskresjonskode = Diskresjonskoder().withValue("SPSF")

        every { personV3Service.hentPerson(any()) } returns bruker

        val actual = diskresjonkodeHelper.hentDiskresjonskode(listOf(p2000))

        Assertions.assertEquals(SPSF, actual)
    }
}
