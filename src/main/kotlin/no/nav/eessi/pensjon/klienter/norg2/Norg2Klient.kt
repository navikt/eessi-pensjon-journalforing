package no.nav.eessi.pensjon.klienter.norg2

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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
class Norg2Klient(private val proxyOAuthRestTemplate: RestTemplate,
                  @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    constructor(): this(RestTemplate())

    private val logger = LoggerFactory.getLogger(Norg2Klient::class.java)

    private lateinit var hentArbeidsfordeling: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentArbeidsfordeling = metricsHelper.init("hentArbeidsfordeling")
    }

    fun hentArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest) : List<Norg2ArbeidsfordelingItem> {
        return hentArbeidsfordeling.measure {

            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val httpEntity = HttpEntity(request.toJson(), headers)

                logger.info("Kaller NORG med : ${request.toJson()}")
                val responseEntity = proxyOAuthRestTemplate.exchange(
                        "/api/v1/arbeidsfordeling",
                        HttpMethod.POST,
                        httpEntity,
                        String::class.java)

                val fordelingEnheter = mapJsonToAny<List<Norg2ArbeidsfordelingItem>>(responseEntity.body!!)
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
}

