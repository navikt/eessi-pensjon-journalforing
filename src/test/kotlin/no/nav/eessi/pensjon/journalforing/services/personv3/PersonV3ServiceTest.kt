package no.nav.eessi.pensjon.journalforing.services.personv3

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import no.nav.eessi.pensjon.journalforing.models.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.journalforing.models.PersonV3SikkerhetsbegrensningException
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
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PersonV3ServiceTest {

    private lateinit var personV3 : PersonV3

    lateinit var personV3Service : PersonV3Service

    private val subject = "23037329381"
    private val ikkeFunnetSubject = "33037329381"
    private val sikkerhetsbegrensingSubject = "43037329381"

    @Before
    fun setup() {
        personV3 = spyk()
        personV3Service = spyk(PersonV3Service(personV3))

        every { personV3Service.konfigurerSamlToken() } just Runs

        every { personV3.hentPerson(requestBuilder(subject, listOf(Informasjonsbehov.ADRESSE))) } returns
                HentPersonResponse().withPerson(PersonMock.createWith(subject))

        every { personV3.hentPerson(requestBuilder(ikkeFunnetSubject, listOf(Informasjonsbehov.ADRESSE))) } throws
                HentPersonPersonIkkeFunnet("$ikkeFunnetSubject ikke funnet", PersonIkkeFunnet())

        every { personV3.hentPerson(requestBuilder(sikkerhetsbegrensingSubject, listOf(Informasjonsbehov.ADRESSE))) } throws
                HentPersonSikkerhetsbegrensning("$sikkerhetsbegrensingSubject har sikkerhetsbegrensning", Sikkerhetsbegrensning())
    }

    @Test
    fun `Kaller hentPerson med gyldig subject`(){
        try {
            assertEquals(personV3Service.hentPerson(subject), PersonMock.createWith(subject))
        }catch(ex: Exception){
            assert(false)
        }
    }

    @Test
    fun `Kaller hentPerson med subject som ikke finnes`(){
        try {
            personV3Service.hentPerson(ikkeFunnetSubject)
            assert(false)
        }catch(ex: Exception){
            assert(ex is PersonV3IkkeFunnetException)
            assertEquals(ex.message, "$ikkeFunnetSubject ikke funnet")
        }
    }

    @Test
    fun `Kaller hentPerson med subject med sikkerhetsbegrensing`(){
        try {
            personV3Service.hentPerson(sikkerhetsbegrensingSubject)
            assert(false)
        }catch(ex: Exception){
            assert(ex is PersonV3SikkerhetsbegrensningException)
            assertEquals(ex.message, "$sikkerhetsbegrensingSubject har sikkerhetsbegrensning")
        }
    }

    @Test
    fun `Kaller hentLandKode med gyldig subject`(){
        try {
            assertEquals("NOR", personV3Service.hentLandKode(PersonMock.createWith(subject)!!))
        }catch(ex: Exception){
            assert(false)
        }
    }

    @Test
    fun `Kaller hentLandKode med subject uten landkode`(){
        try {
            assertEquals(null,  personV3Service.hentLandKode(PersonMock.createWith(subject, false)!!))
        }catch(ex: Exception){
            assert(false)
        }
    }

    fun requestBuilder(norskIdent: String, informasjonsbehov: List<Informasjonsbehov>): HentPersonRequest{
        return HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(norskIdent)))
            withInformasjonsbehov(informasjonsbehov)
        }
    }

}
