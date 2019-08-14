package no.nav.eessi.pensjon.services.personv3

import io.micrometer.core.instrument.Metrics.counter
import no.nav.eessi.pensjon.security.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus

fun hentLandkode(person: Person?) =
        person?.bostedsadresse?.strukturertAdresse?.landkode?.value

fun hentPersonNavn(person: Person?) =
        person?.personnavn?.sammensattNavn


@Service
class PersonV3Service(private val service: PersonV3) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonV3Service::class.java) }

    private val hentpersonVellykkede = counter("eessipensjon_journalforing", "http_request", "hentperson", "type", "vellykkede")
    private val hentpersonFeilede = counter("eessipensjon_journalforing", "http_request", "hentperson", "type", "feilede")


    fun hentPerson(fnr: String): Person {
        logger.info("Henter person fra PersonV3Service")

        try {
            logger.info("Kaller PersonV3.hentPerson service")
            val resp = kallPersonV3(fnr)
            hentpersonVellykkede.increment()
            return resp.person as Person
        } catch (personIkkefunnet : HentPersonPersonIkkeFunnet) {
            logger.error("Kaller PersonV3.hentPerson service Feilet")
            hentpersonFeilede.increment()
            throw PersonV3IkkeFunnetException(personIkkefunnet.message)
        } catch (personSikkerhetsbegrensning: HentPersonSikkerhetsbegrensning) {
            logger.error("Kaller PersonV3.hentPerson service Feilet")
            hentpersonFeilede.increment()
            throw PersonV3SikkerhetsbegrensningException(personSikkerhetsbegrensning.message)
        }
    }

    private fun kallPersonV3(fnr: String?) : HentPersonResponse{

        val request = HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr)))

            withInformasjonsbehov(listOf(
                    Informasjonsbehov.ADRESSE))
        }
        konfigurerSamlToken()
        return  service.hentPerson(request)
    }

    fun konfigurerSamlToken(){
        configureRequestSamlToken(service)
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class PersonV3IkkeFunnetException(message: String?): Exception(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class PersonV3SikkerhetsbegrensningException(message: String?): Exception(message)
