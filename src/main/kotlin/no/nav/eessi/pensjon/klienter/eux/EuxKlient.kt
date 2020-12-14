package no.nav.eessi.pensjon.klienter.eux

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.sed.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import javax.annotation.PostConstruct

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class EuxKlient(
        private val euxOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxKlient::class.java) }

    private lateinit var hentpdf: MetricsHelper.Metric
    private lateinit var hentSed: MetricsHelper.Metric
    private lateinit var hentBuc: MetricsHelper.Metric


    @PostConstruct
    fun initMetrics() {
        hentpdf = metricsHelper.init("hentpdf", alert = MetricsHelper.Toggle.OFF)
        hentSed = metricsHelper.init("hentSed", alert = MetricsHelper.Toggle.OFF)
        hentBuc = metricsHelper.init("hentBuc", alert = MetricsHelper.Toggle.OFF)

    }

    /**
     * Henter SED og tilhørende vedlegg i json format
     *
     * @param rinaNr BUC-id
     * @param dokumentId SED-id
     */
    @Retryable(include = [HttpStatusCodeException::class]
            , backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0))
    fun hentSedDokumenter(rinaNr: String, dokumentId: String): String? {
        return hentpdf.measure {
            val path = "/buc/$rinaNr/sed/$dokumentId/filer"
            return@measure try {
                logger.info("Henter PDF for SED og tilhørende vedlegg for rinaNr: $rinaNr , dokumentId: $dokumentId")
                euxOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch(ex: Exception) {
                logger.warn("En feil oppstod under henting av PDF", ex)
                throw ex
            }
        }
    }

    /**
     * Henter SED i json format
     *
     * @param rinaNr BUC-id
     * @param dokumentId SED-id
     */
    /*@Retryable(include = [HttpStatusCodeException::class], backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0))
    fun hentSed(rinaNr: String, dokumentId: String): String? {
        return hentSed.measure {
            val path = "/buc/$rinaNr/sed/$dokumentId"
            return@measure try {
                logger.info("Henter SED for rinaNr: $rinaNr , dokumentId: $dokumentId")
                euxOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch (ex: Exception) {
                logger.warn("En feil oppstod under henting av SED ex: $path", ex)
                throw ex
            }
        }
    }*/

    /**
     * Henter SED i json format
     *
     * @param rinaNr BUC-id
     * @param dokumentId SED-id
     */
    @Retryable(include = [HttpStatusCodeException::class], backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0))
    fun hentSed(rinaNr: String, dokumentId: String): SED? {
        return hentSed.measure {
            val path = "/buc/$rinaNr/sed/$dokumentId"
            return@measure try {
                logger.info("Henter SED for rinaNr: $rinaNr , dokumentId: $dokumentId")

                euxOidcRestTemplate.getForObject(path, SED::class.java)
            } catch (ex: Exception) {
                logger.warn("En feil oppstod under henting av SED ex: $path", ex)
                throw ex
            }
        }
    }
}