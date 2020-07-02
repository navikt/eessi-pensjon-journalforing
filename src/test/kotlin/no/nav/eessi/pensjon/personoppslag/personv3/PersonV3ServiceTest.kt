package no.nav.eessi.pensjon.personoppslag.personv3

import io.mockk.every
import io.mockk.spyk
import no.nav.eessi.pensjon.security.sts.STSClientConfig
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.feil.Sikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PersonV3ServiceTest {

    private lateinit var personV3 : PersonV3

    @Mock
    private lateinit var stsClientConfig: STSClientConfig

    lateinit var personV3Service : PersonV3Service

    private val subject = "23037329381"
    private val ikkeFunnetSubject = "33037329382"
    private val sikkerhetsbegrensingSubject = "43037329383"

    @BeforeEach
    fun setup() {
        personV3 = spyk()
        personV3Service = PersonV3Service(personV3, stsClientConfig)
        personV3Service.initMetrics()
    }

    @Test
    fun `Kaller hentPerson med gyldig subject`(){
        every {personV3.hentPerson(any())} returns HentPersonResponse.createWith(subject)

        try {
            val person = personV3Service.hentPerson(subject)
            assertEquals("23037329381", (person!!.aktoer as PersonIdent).ident.ident)
        }catch(ex: Exception){
            assert(false)
        }
    }

    @Test
    fun `Kaller hentPerson med subject som ikke finnes`(){
        every {personV3.hentPerson(any())} throws HentPersonPersonIkkeFunnet("whoooops", PersonIkkeFunnet())

        try {
            val person = personV3Service.hentPerson(ikkeFunnetSubject)
            assertNull(person)
        }catch(ex: Exception){
           fail("Skulle ikke ha kastet exeption ved person ikke funnet, men returnert null istedet")
        }
    }

    @Test
    fun `Kaller hentPerson med subject med sikkerhetsbegrensing`(){
        every {personV3.hentPerson(any())} throws HentPersonSikkerhetsbegrensning("$sikkerhetsbegrensingSubject har sikkerhetsbegrensning", Sikkerhetsbegrensning())

        try {
            personV3Service.hentPerson(sikkerhetsbegrensingSubject)
            assert(false)
        }catch(ex: Exception){
            assert(ex is PersonV3SikkerhetsbegrensningException)
            assertEquals(ex.message, "$sikkerhetsbegrensingSubject har sikkerhetsbegrensning")
        }
    }
}
