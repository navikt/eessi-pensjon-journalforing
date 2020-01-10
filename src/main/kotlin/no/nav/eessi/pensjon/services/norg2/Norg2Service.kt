package no.nav.eessi.pensjon.services.norg2

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class Norg2Service(private val norg2OidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    constructor(): this(RestTemplate())

    private val logger = LoggerFactory.getLogger(Norg2Service::class.java)

    //https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper
    protected enum class BehandlingsTyper(val kode : String) {
        BOSATT_NORGE("ae0104"),
        BOSATT_UTLAND("ae0107")
    }

    fun hentArbeidsfordelingEnhet(person: IdentifisertPerson): String? {
        val request = opprettNorg2ArbeidsfordelingRequest(person)
        logger.debug("følgende request til Norg2 : $request")
        val enheter = hentArbeidsfordelingEnheter(request)

        return finnKorrektArbeidsfordelingEnheter(request, enheter)
    }

    fun opprettNorg2ArbeidsfordelingRequest(person: IdentifisertPerson): Norg2ArbeidsfordelingRequest {
        return when {
            person.landkode == "NOR" && person.geografiskTilknytning != null && person.diskresjonskode == null -> Norg2ArbeidsfordelingRequest(
                    geografiskOmraade = person.geografiskTilknytning,
                    behandlingstype = BehandlingsTyper.BOSATT_NORGE.kode
            )
            person.landkode != "NOR" && person.diskresjonskode == null -> Norg2ArbeidsfordelingRequest(
                    geografiskOmraade = "ANY",
                    behandlingstype = BehandlingsTyper.BOSATT_UTLAND.kode
            )
            person.diskresjonskode != null && person.diskresjonskode == "SPSF" -> Norg2ArbeidsfordelingRequest(
                    tema = "ANY",
                    diskresjonskode = "SPSF"
            )
            else -> throw Norg2ArbeidsfordelingRequestException("Feiler ved oppretting av request")
        }
    }

    fun hentArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest) : List<Norg2ArbeidsfordelingItem>? {
        return metricsHelper.measure("hentArbeidsfordeling") {

            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val httpEntity = HttpEntity(request.toJson(), headers)

                val responseEntity = norg2OidcRestTemplate.exchange(
                        "/api/v1/arbeidsfordeling",
                        HttpMethod.POST,
                        httpEntity,
                        String::class.java)

                val fordelingEnheter = mapJsonToAny(responseEntity.body!!, typeRefs<List<Norg2ArbeidsfordelingItem>>())
                logger.debug("fordelsingsEnheter: $fordelingEnheter")

                fordelingEnheter

            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av arbeidsfordeling ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under henting av arbeidsfordeling ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av arbeidsfordeling ex: $ex")
                throw RuntimeException("En feil oppstod under henting av arbeidsfordeling ex: ${ex.message}")
            }
        }
    }

    fun finnKorrektArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest, list: List<Norg2ArbeidsfordelingItem>?): String? {
        return list
                ?.asSequence()
                ?.filter { it.diskresjonskode == request.diskresjonskode }
                ?.filter { it.behandlingstype == request.behandlingstype }
                ?.filter { it.tema == request.tema }
                ?.map { it.enhetNr }
                ?.lastOrNull()
    }
}


class Norg2ArbeidsfordelingRequestException(melding: String): RuntimeException(melding)

class Norg2ArbeidsfordelingRequest(
    val tema: String = "PEN",
    val diskresjonskode: String? = "ANY",
    val behandlingstema: String = "ANY",
    val behandlingstype: String = "ANY",
    val geografiskOmraade: String = "ANY",
    val skalTilLokalkontor: Boolean = false,
    val oppgavetype: String = "ANY",
    val temagruppe: String = "ANY"
)

class Norg2ArbeidsfordelingItem(
    val oppgavetype: String? = null,
    val enhetNr: String? = null,
    val behandlingstema: String? = null,
    val temagruppe: String? = null,
    val skalTilLokalkontor: Boolean? = null,
    val behandlingstype: String? = null,
    val geografiskOmraade: String? = null,
    val tema: String? = null,
    val enhetNavn: String? = null,
    val diskresjonskode: String? = null,
    val gyldigFra: String? = null,
    val enhetId: Int? = null,
    val id: Int? = null
)
