package no.nav.eessi.pensjon.personoppslag.personv3

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.security.sts.STSClientConfig
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import javax.annotation.PostConstruct
import javax.xml.ws.soap.SOAPFaultException

fun hentLandkode(person: Person) =
        person.bostedsadresse?.strukturertAdresse?.landkode?.value

fun hentPersonNavn(person: Person) =
        person.personnavn?.sammensattNavn

fun hentGeografiskTilknytning(bruker: Bruker?) = bruker?.geografiskTilknytning?.geografiskTilknytning

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class PersonV3Service(
        private val service: PersonV3,
        private val stsClientConfig: STSClientConfig,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonV3Service::class.java) }

    private lateinit var hentperson: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentperson = metricsHelper.init("hentperson")
    }

    fun hentPerson(fnr: String): Bruker? {
        return hentperson.measure {
            logger.info("Henter person fra PersonV3Klient")

            try {
                logger.info("Kaller PersonV3.hentPerson")
                val resp = kallPersonV3(fnr)
                resp.person as Bruker
            } catch (pif: HentPersonPersonIkkeFunnet) {
                logger.warn("Personen finnes ikke", pif)
                null
            } catch (sfe: SOAPFaultException) {
                if (sfe.fault.faultString.contains("F002001F")) {
                    logger.warn("PersonV3: Kunne ikke hente person, ugyldig input", sfe)
                    null
                } else {
                    logger.error("PersonV3: Ukjent SoapFaultException", sfe)
                    throw sfe
                }
            } catch (sb: HentPersonSikkerhetsbegrensning) {
                logger.error("PersonV3: Kunne ikke hente person, sikkerhetsbegrensning", sb)
                throw PersonV3SikkerhetsbegrensningException(sb.message)
            } catch (ex: Exception) {
                logger.error("PersonV3: Kunne ikke hente person", ex)
                throw ex
            }
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
        stsClientConfig.configureRequestSamlToken(service)
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class PersonV3IkkeFunnetException(message: String?): Exception(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class PersonV3SikkerhetsbegrensningException(message: String?): Exception(message)
