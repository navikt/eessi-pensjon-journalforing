package no.nav.eessi.pensjon.services.eux

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class EuxService(
        private val euxOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxService::class.java) }
    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

    /**
     * Henter SED og tilhørende vedlegg i json format
     *
     * @param rinaNr BUC-id
     * @param dokumentId SED-id
     */
    fun hentSedDokumenter(rinaNr: String, dokumentId: String): String? {
        return metricsHelper.measure("hentpdf") {
            val path = "/buc/$rinaNr/sed/$dokumentId/filer"
            return@measure try {
                logger.info("Henter PDF for SED og tilhørende vedlegg for rinaNr: $rinaNr , dokumentId: $dokumentId")
                euxOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av PDF ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod henting av PDF ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av PDF ex: $ex")
                throw RuntimeException("En feil oppstod under henting av PDF ex: ${ex.message}")
            }
        }
    }

    /**
     * Henter SED i json format
     *
     * @param rinaNr BUC-id
     * @param dokumentId SED-id
     */
    fun hentSed(rinaNr: String, dokumentId: String) : String? {
        return metricsHelper.measure("hentSed") {
            val path = "/buc/$rinaNr/sed/$dokumentId"
            return@measure try {
                logger.info("Henter SED for rinaNr: $rinaNr , dokumentId: $dokumentId")
                euxOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av SED ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under henting av SED ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av SED ex: $ex")
                throw RuntimeException("En feil oppstod under henting av SED ex: ${ex.message}")
            }
        }
    }

    /**
     * Henter den forsikredes fødselsdato i en konkret SED
     *
     * @param rinaNr BUC-id
     * @param dokumentId SED-id
     */
    fun hentFodselsDatoFraSed(rinaNr: String, dokumentId: String): String? {
        val sed = hentSed(rinaNr, dokumentId)
        val rootNode = mapper.readValue(sed, JsonNode::class.java)
        val foedselsdatoNode = rootNode.path("nav")
                .path("bruker")
                .path("person")
                .path("foedselsdato")
        return foedselsdatoNode.textValue()
    }
}
