package no.nav.eessi.pensjon.journalforing.services.personv3

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.journalforing.config.TimingService
import no.nav.eessi.pensjon.journalforing.models.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.journalforing.models.PersonV3SikkerhetsbegrensningException
import no.nav.eessi.pensjon.journalforing.services.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PersonV3Service(val service: PersonV3,
                      val timingService: TimingService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonV3Service::class.java) }

    private val hentperson_teller_navn = "eessipensjon_journalforing.hentperson"
    private val hentperson_teller_type_vellykkede = counter(hentperson_teller_navn, "vellykkede")
    private val hentperson_teller_type_feilede = counter(hentperson_teller_navn, "feilede")

    final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    fun hentPerson(fnr: String): no.nav.tjeneste.virksomhet.person.v3.informasjon.Person {
        logger.info("Henter person fra PersonV3Service")
        configureRequestSamlToken(service)

        val request = HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr)))

            withInformasjonsbehov(listOf(
                    Informasjonsbehov.ADRESSE))
        }
        val persontimed = timingService.timedStart("personV3")
        try {
            logger.info("Kaller PersonV3.hentPerson service")
            val resp = service.hentPerson(request)
            hentperson_teller_type_vellykkede.increment()
            timingService.timesStop(persontimed)
            return resp.person as no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
        } catch (personIkkefunnet : HentPersonPersonIkkeFunnet) {
            logger.error("Kaller PersonV3.hentPerson service Feilet")
            timingService.timesStop(persontimed)
            hentperson_teller_type_feilede.increment()
            throw PersonV3IkkeFunnetException(personIkkefunnet.message)
        } catch (personSikkerhetsbegrensning: HentPersonSikkerhetsbegrensning) {
            logger.error("Kaller PersonV3.hentPerson service Feilet")
            timingService.timesStop(persontimed)
            hentperson_teller_type_feilede.increment()
            throw PersonV3SikkerhetsbegrensningException(personSikkerhetsbegrensning.message)
        }
    }


    fun hentLandKode(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String? {

        //ikke adresse for død
        if (person.personstatus.equals("DØD")) {
            logger.debug("           Person er avdod (ingen adresse å hente).")
            return null
        }

        val bostedsadresse: Bostedsadresse = person.bostedsadresse ?: return null

        return bostedsadresse.strukturertAdresse.landkode.value
    }
}