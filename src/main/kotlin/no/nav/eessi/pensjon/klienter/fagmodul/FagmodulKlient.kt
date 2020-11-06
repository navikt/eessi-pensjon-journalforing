package no.nav.eessi.pensjon.klienter.fagmodul

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SakInformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import javax.annotation.PostConstruct


/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class FagmodulKlient(
        private val fagmodulOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(FagmodulKlient::class.java) }

    private lateinit var hentYtelseKravtype: MetricsHelper.Metric
    private lateinit var hentFodselsdatoFraBuc: MetricsHelper.Metric
    private lateinit var hentSeds: MetricsHelper.Metric
    private lateinit var hentFnrFraBUC: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentYtelseKravtype = metricsHelper.init("hentYtelseKravtype")
        hentFodselsdatoFraBuc = metricsHelper.init("hentFodselsdatoFraBuc")
        hentSeds = metricsHelper.init("hentSeds")
        hentFnrFraBUC = metricsHelper.init("hentFnrFraBUC")
    }

    fun hentAlleDokumenter(rinaNr: String): String? {
        return hentSeds.measure {
            val path = "/buc/$rinaNr/allDocuments"
            return@measure try {
                logger.info("Henter jsondata for alle sed for rinaNr: $rinaNr")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        null,
                        String::class.java).body
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av alledokumenter ex: $ex body: ${ex.responseBodyAsString}", ex)
                throw RuntimeException("En feil oppstod under henting av alledokumenter ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av alledokumenter ex: $ex", ex)
                throw RuntimeException("En feil oppstod under henting av alledokumenter ex: ${ex.message}")
            }
        }
    }

    fun hentPensjonSaklist(aktoerId: String): List<SakInformasjon> {
        val path = "/pensjon/sakliste/$aktoerId"
        try {
            val responsebody = fagmodulOidcRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java).body
            val json = responsebody.orEmpty()
            return mapJsonToAny(json, typeRefs<List<SakInformasjon>>())
        } catch(ex: HttpStatusCodeException) {
            logger.error("En feil oppstod under henting av pensjonsakliste ex: $ex body: ${ex.responseBodyAsString}", ex)
            throw RuntimeException("En feil oppstod under henting av pensjonsakliste ex: ${ex.message} body: ${ex.responseBodyAsString}")
        } catch(ex: Exception) {
            logger.error("En feil oppstod under henting av pensjonsakliste ex: $ex", ex)
            throw RuntimeException("En feil oppstod under henting av pensjonsakliste ex: ${ex.message}")
        }
    }

}
