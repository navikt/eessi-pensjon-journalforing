package no.nav.eessi.pensjon.personidentifisering

import io.mockk.mockk
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSøk
import no.nav.eessi.pensjon.personidentifisering.klienter.AktoerregisterKlient
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import no.nav.eessi.pensjon.security.sts.STSClientConfig
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersonidentifiseringTest {

    @Test @Disabled("Work in progress")
    fun `happy day scenario`() {
        //given
        val aktoerregisterKlient = mockk<AktoerregisterKlient>()
        val personV3 = mockk<PersonV3>()
        val personV3Klient = PersonV3Klient(personV3, mockk<STSClientConfig>())
        val diskresjonService = DiskresjonkodeHelper(personV3Klient, SedFnrSøk())
        val fnrHelper = FnrHelper(personV3Klient)
        val fdatoHelper = FdatoHelper()
        val personidentifiseringService = PersonidentifiseringService(aktoerregisterKlient, personV3Klient, diskresjonService, fnrHelper, fdatoHelper)

        //when
        val navBruker = null
        val alleSediBuc = emptyList<String?>()

        val actual = personidentifiseringService.identifiserPerson(navBruker, alleSediBuc)

        // then
        val expected = IdentifisertPerson(
                fnr = null,
                aktoerId = null,
                fdato = LocalDate.now(),
                personNavn = null,
                diskresjonskode = null,
                landkode = null,
                geografiskTilknytning = null
        )
        assertEquals(expected, actual)
    }
}
