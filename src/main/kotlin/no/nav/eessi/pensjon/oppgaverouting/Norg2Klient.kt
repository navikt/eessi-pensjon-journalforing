package no.nav.eessi.pensjon.oppgaverouting

import com.google.common.annotations.VisibleForTesting
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import javax.annotation.PostConstruct

@Component
class Norg2Klient(private val norg2OidcRestTemplate: RestTemplate,
                  @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    constructor(): this(RestTemplate())

    private val logger = LoggerFactory.getLogger(Norg2Klient::class.java)

    private lateinit var hentArbeidsfordeling: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentArbeidsfordeling = metricsHelper.init("hentArbeidsfordeling")
    }

    //https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper
    protected enum class BehandlingsTyper(val kode : String) {
        BOSATT_NORGE("ae0104"),
        BOSATT_UTLAND("ae0107")
    }

    fun hentArbeidsfordelingEnhet(person: NorgKlientRequest): String? {
        val request = opprettNorg2ArbeidsfordelingRequest(person)
        logger.debug("følgende request til Norg2 : $request")
        val enheter = hentArbeidsfordelingEnheter(request)

        return finnArbeidsfordelingEnheter(request, enheter)
    }

    fun opprettNorg2ArbeidsfordelingRequest(req: NorgKlientRequest): Norg2ArbeidsfordelingRequest {
        if (req.harAdressebeskyttelse)
            return Norg2ArbeidsfordelingRequest(tema = "ANY", diskresjonskode = "SPSF")

        val behandlingstype = if (req.landkode === "NOR")
            BehandlingsTyper.BOSATT_NORGE.kode
        else BehandlingsTyper.BOSATT_UTLAND.kode

        return Norg2ArbeidsfordelingRequest(
            geografiskOmraade = req.geografiskTilknytning ?: "ANY",
            behandlingstype = behandlingstype
        )
    }

    fun hentArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest) : List<Norg2ArbeidsfordelingItem> {
        return hentArbeidsfordeling.measure {

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

    @VisibleForTesting
    fun finnArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest, list: List<Norg2ArbeidsfordelingItem>): String? {
        return list.asSequence()
                .filter { it.diskresjonskode == request.diskresjonskode }
                .filter { it.behandlingstype == request.behandlingstype }
                .filter { it.tema == request.tema }
                .map { it.enhetNr }
                .lastOrNull()
    }
}

data class NorgKlientRequest(val harAdressebeskyttelse: Boolean = false,
                             val landkode: String? = null,
                             val geografiskTilknytning: String? = null)

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
