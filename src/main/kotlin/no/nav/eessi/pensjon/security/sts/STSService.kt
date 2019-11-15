package no.nav.eessi.pensjon.security.sts

import com.fasterxml.jackson.annotation.JsonProperty
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import javax.annotation.PostConstruct
import no.nav.eessi.pensjon.json.mapAnyToJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.*
import java.lang.RuntimeException


inline fun <reified T : Any> typeRef(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}

data class SecurityTokenResponse(
        @JsonProperty("access_token")
        val accessToken: String,
        @JsonProperty("token_type")
        val tokenType: String,
        @JsonProperty("expires_in")
        val expiresIn: Long
)

data class WellKnownSTS(
        @JsonProperty("issuer")
        val issuer: String,
        @JsonProperty("token_endpoint")
        val tokenEndpoint: String,
        @JsonProperty("exchange_token_endpoint")
        val exchangeTokenEndpoint: String,
        @JsonProperty("jwks_uri")
        val jwksUri: String,
        @JsonProperty("subject_types_supported")
        val subjectTypesSupported: List<String>
)
/**
 * Denne STS tjenesten benyttes ved kall mot nye REST tjenester sånn som Aktørregisteret
 *
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class STSService(
        private val securityTokenExchangeBasicAuthRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(STSService::class.java)

    @Value("\${securityTokenService.discoveryUrl}")
    lateinit var discoveryUrl: String

    lateinit var wellKnownSTS: WellKnownSTS

    @PostConstruct
    fun discoverEndpoints() {
        metricsHelper.measure("disoverSTS") {
            try {
                logger.info("Henter STS endepunkter fra well.known " + discoveryUrl)
                    wellKnownSTS = RestTemplate().exchange(discoveryUrl,
                            HttpMethod.GET,
                            null,
                            typeRef<WellKnownSTS>()).body!!
            } catch (ex: Exception) {
                logger.error("Feil ved henting av STS endepunkter fra well.known: ${ex.message}", ex)
                throw RestClientException(ex.message!!)
            }
        }
    }

    fun getSystemOidcToken(): String {
        return metricsHelper.measure("getSystemOidcToken") {
            try {
                val uri = UriComponentsBuilder.fromUriString(wellKnownSTS.tokenEndpoint)
                        .queryParam("grant_type", "client_credentials")
                        .queryParam("scope", "openid")
                        .build().toUriString()

                logger.info("Kaller STS for å bytte username/password til OIDC token")
                val responseEntity = securityTokenExchangeBasicAuthRestTemplate.exchange(uri,
                        HttpMethod.GET,
                        null,
                        typeRef<SecurityTokenResponse>())

                logger.debug("SecurityTokenResponse ${mapAnyToJson(responseEntity)} ")
                responseEntity.body!!.accessToken
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under bytting av username/password til OIDC token ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under bytting av username/password til OIDC token ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under bytting av username/password til OIDC token ex: $ex")
                throw RuntimeException("En feil oppstod under bytting av username/password til OIDC token ex: ${ex.message}")
            }
        }
    }
}
