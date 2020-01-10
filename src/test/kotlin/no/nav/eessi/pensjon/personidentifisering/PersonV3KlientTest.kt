package no.nav.eessi.pensjon.personidentifisering

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3SikkerhetsbegrensningException
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.feil.Sikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Informasjonsbehov
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PersonV3KlientTest {

    private lateinit var personV3 : PersonV3

    lateinit var personV3Klient : PersonV3Klient

    private val subject = "23037329381"
    private val ikkeFunnetSubject = "33037329381"
    private val sikkerhetsbegrensingSubject = "43037329381"

    @BeforeEach
    fun setup() {
        personV3 = spyk()
        personV3Klient = spyk(PersonV3Klient(personV3))

        every { personV3Klient.konfigurerSamlToken() } just Runs

        every { personV3.hentPerson(requestBuilder(subject, listOf(Informasjonsbehov.ADRESSE))) } returns
                HentPersonResponse().withPerson(BrukerMock.createWith())

        every { personV3.hentPerson(requestBuilder(ikkeFunnetSubject, listOf(Informasjonsbehov.ADRESSE))) } throws
                HentPersonPersonIkkeFunnet("$ikkeFunnetSubject ikke funnet", PersonIkkeFunnet())

        every { personV3.hentPerson(requestBuilder(sikkerhetsbegrensingSubject, listOf(Informasjonsbehov.ADRESSE))) } throws
                HentPersonSikkerhetsbegrensning("$sikkerhetsbegrensingSubject har sikkerhetsbegrensning", Sikkerhetsbegrensning())
    }

    @Test
    fun `Kaller hentPerson med gyldig subject`(){
        try {
            assertEquals(personV3Klient.hentPerson(subject), BrukerMock.createWith())
        }catch(ex: Exception){
            assert(false)
        }
    }

    @Test
    fun `Kaller hentPerson med subject som ikke finnes`(){
        try {
            val person = personV3Klient.hentPerson(ikkeFunnetSubject)
            assertNull(person)
        }catch(ex: Exception){
           fail("Skulle ikke ha kastet exeption ved person ikke funnet, men returnert null istedet")
        }
    }

    @Test
    fun `Kaller hentPerson med subject med sikkerhetsbegrensing`(){
        try {
            personV3Klient.hentPerson(sikkerhetsbegrensingSubject)
            assert(false)
        }catch(ex: Exception){
            assert(ex is PersonV3SikkerhetsbegrensningException)
            assertEquals(ex.message, "$sikkerhetsbegrensingSubject har sikkerhetsbegrensning")
        }
    }

    fun requestBuilder(norskIdent: String, informasjonsbehov: List<Informasjonsbehov>): HentPersonRequest{
        return HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(norskIdent)))
            withInformasjonsbehov(informasjonsbehov)
        }
    }

}
