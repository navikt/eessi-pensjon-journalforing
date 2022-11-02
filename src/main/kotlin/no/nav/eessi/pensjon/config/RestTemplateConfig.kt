package no.nav.eessi.pensjon.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
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
import org.springframework.http.client.*
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.util.*
import java.util.concurrent.TimeUnit


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
        return opprettRestTemplateForJoark(joarkUrl, "dokarkiv-credentials")
    }

    @Bean
    fun fagmodulOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return opprettRestTemplate(fagmodulUrl, "fagmodul-credentials")
    }

    @Bean
    fun bestemSakOidcRestTemplate(): RestTemplate {
        return opprettRestTemplate(bestemSakUrl, "proxy-credentials")
    }

    /**
     * Denne bruker HttpComponentsClientHttpRequestFactory - angivelig for å fikse
     * problemer med HTTP-PATCH – som brukes mot joark.
     */
    private fun opprettRestTemplateForJoark(url: String, oAuthKey: String) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestCountInterceptor(meterRegistry),
                ResourceAccessRetryInterceptor(),
                bearerTokenInterceptor(clientProperties(oAuthKey), oAuth2AccessTokenService!!)
            )
            .build().apply {
                requestFactory = HttpComponentsClientHttpRequestFactory()
            }
    }

    private fun opprettRestTemplate(url: String, oAuthKey: String) : RestTemplate {
        return RestTemplateBuilder()
                .rootUri(url)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        ResourceAccessRetryInterceptor(),
                        bearerTokenInterceptor(clientProperties(oAuthKey), oAuth2AccessTokenService!!)
                )
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()
                            .apply { setOutputStreaming(false) }
                    )
                }
    }


    private fun clientProperties(oAuthKey: String): ClientProperties {
        return Optional.ofNullable(clientConfigurationProperties.registration[oAuthKey])
            .orElseThrow { RuntimeException("could not find oauth2 client config for example-onbehalfof") }
    }

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor? {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            execution.execute(request, body!!)
        }
    }

    internal class ResourceAccessRetryInterceptor : ClientHttpRequestInterceptor {
        private val logger = LoggerFactory.getLogger(ResourceAccessRetryInterceptor::class.java)

        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution) =
            withRetries { execution.execute(request, body) }

        private fun <T> withRetries(maxAttempts: Int = 3, waitTime: Long = 1L, timeUnit: TimeUnit = TimeUnit.SECONDS, func: () -> T): T {
            var failException: Throwable? = null
            var count = 0
            while (count < maxAttempts) {
                try {
                    return func.invoke()
                } catch (ex: ResourceAccessException) { // Dette bør ta seg av IOException - som typisk skjer der som det er nettverksissues.
                    count++
                    logger.warn("Attempt $count failed with ${ex.message} caused by ${ex.cause}")
                    failException = ex
                    Thread.sleep(timeUnit.toMillis(waitTime))
                }
            }
            logger.warn("Giving up after $count attempts.")
            throw failException!!
        }
    }

}

