package no.nav.eessi.pensjon.journalforing.services.sts

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.journalforing.SystembrukerTokenException
import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.json.mapAnyToJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import javax.annotation.PostConstruct

private val logger = LoggerFactory.getLogger(STSService::class.java)

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
 */
@Service
class STSService(val securityTokenExchangeBasicAuthRestTemplate: RestTemplate) {

    private final val discoverSTSEndpointsNavn = "eessipensjon_journalforing.discoverSTS"
    private val discoverSTSEndpointsVellykkede = counter(discoverSTSEndpointsNavn, "vellykkede")
    private val discoverSTSEndpointsFeilede = counter(discoverSTSEndpointsNavn, "feilede")

    private final val getSystemOidcTokenNavn = "eessipensjon_journalforing.getSystemOidcToken"
    private val getSystemOidcTokenVellykkede = counter(getSystemOidcTokenNavn, "vellykkede")
    private val getSystemOidcTokenFeilede = counter(getSystemOidcTokenNavn, "feilede")

    @Value("\${securityTokenService.discoveryUrl}")
    lateinit var discoveryUrl: String

    lateinit var wellKnownSTS: WellKnownSTS

    @PostConstruct
    fun discoverEndpoints(){
        try {
            logger.info("Henter STS endepunkter fra well.known " + discoveryUrl)
            wellKnownSTS = RestTemplate().exchange(discoveryUrl,
                    HttpMethod.GET,
                    null,
                    typeRef<WellKnownSTS>()).body!!
            discoverSTSEndpointsVellykkede.increment()
        } catch (ex: Exception) {
            logger.error("Feil ved henting av STS endepunkter fra well.known: ${ex.message}", ex)
            discoverSTSEndpointsFeilede.increment()
            throw RestClientException(ex.message!!)
        }
    }

    fun getSystemOidcToken(): String {
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
            validateResponse(responseEntity)
            getSystemOidcTokenVellykkede.increment()
            return responseEntity.body!!.accessToken
        } catch (ex: Exception) {
            logger.error("Feil ved bytting av username/password til OIDC token: ${ex.message}", ex)
            getSystemOidcTokenFeilede.increment()
            throw SystembrukerTokenException(ex.message!!)
        }
    }

    private fun validateResponse(responseEntity: ResponseEntity<SecurityTokenResponse>) {
        if (responseEntity.statusCode.isError)
            throw RuntimeException("SecurityTokenExchange received http-error ${responseEntity.statusCode}:${responseEntity.statusCodeValue}")
    }
}