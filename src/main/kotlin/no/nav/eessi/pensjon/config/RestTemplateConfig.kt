package no.nav.eessi.pensjon.config

import com.nimbusds.jwt.JWTClaimsSet
import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
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
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.util.*

@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
    private val clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService?,
    private val meterRegistry: MeterRegistry,
    ) {

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Value("\${JOURNALPOST_V1_URL}")
    lateinit var joarkUrl: String

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var euxUrl: String

    @Value("\${EESSI_PENSJON_FAGMODUL_URL}")
    lateinit var fagmodulUrl: String

    @Value("\${NORG2_URL}")
    lateinit var norg2Url: String

    @Value("\${BESTEMSAK_URL}")
    lateinit var bestemSakUrl: String

    @Value("\${EESSI_PEN_ONPREM_PROXY_URL}")
    lateinit var proxyUrl: String



    @Bean
    fun euxOAuthRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate? {
        return opprettRestTemplate(euxUrl, "eux-credentials")
    }

    @Bean
    fun proxyOAuthRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate? {
        return opprettRestTemplate(proxyUrl, "proxy-credentials")
    }

    @Bean
    fun journalpostOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return opprettRestTemplate(joarkUrl, "dokarkiv-credentials")
    }

    @Bean
    fun fagmodulOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return opprettRestTemplate(fagmodulUrl, "fagmodul-credentials")
    }

    @Bean
    fun bestemSakOidcRestTemplate(): RestTemplate {
        return opprettRestTemplate(bestemSakUrl, "pen-credentials")
    }

    private fun opprettRestTemplate(url: String, oAuthKey: String) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestResponseLoggerInterceptor(),
                RequestCountInterceptor(meterRegistry),
                bearerTokenInterceptor(clientProperties(oAuthKey), oAuth2AccessTokenService!!)
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }


    private fun clientProperties(oAuthKey: String): ClientProperties {
        val clientProperties =
            Optional.ofNullable(clientConfigurationProperties.registration[oAuthKey])
                .orElseThrow { RuntimeException("could not find oauth2 client config for example-onbehalfof") }
        return clientProperties
    }

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor? {
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

