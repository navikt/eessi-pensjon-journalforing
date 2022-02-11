package no.nav.eessi.pensjon.config;

import com.nimbusds.jwt.JWTClaimsSet
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate
import java.util.*


@Profile("prod", "test")
@Configuration
class OAuth2Configuration {

    private val logger = LoggerFactory.getLogger(OAuth2Configuration::class.java)

    @Value("\${EUX_RINA_API_V1_URL}")
    private lateinit var euxUrl: String

    /**
     * Create one RestTemplate per OAuth2 client entry to separate between different scopes per API
     */
    @Bean
    fun downstreamClientCredentialsResourceRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService?
    ): RestTemplate? {
        val clientProperties =
            Optional.ofNullable(clientConfigurationProperties.registration["eux-credentials"])
                .orElseThrow { RuntimeException("could not find oauth2 client config for example-onbehalfof") }
        return restTemplateBuilder
            .rootUri(euxUrl)
            .additionalInterceptors(bearerTokenInterceptor(clientProperties, oAuth2AccessTokenService!!))
            .build()
    }

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService): ClientHttpRequestInterceptor? {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            val tokenChunks = response.accessToken.split(".")
            val tokenBody =  tokenChunks[1]
            logger.info("subject: " + JWTClaimsSet.parse(Base64.getDecoder().decode(tokenBody).decodeToString()).subject)
            execution.execute(request, body!!)
        }
    }
}