package no.nav.eessi.pensjon.config

import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.nimbusds.jwt.JWTClaimsSet
import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.client.*
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

    @Value("\${NAVANSATT_URL}")
    lateinit var navansattUrl: String

    @Value("\${SAF_GRAPHQL_URL}")
    lateinit var graphQlUrl: String

    @Value("\${ETTERLATTE_URL}")
    lateinit var etterlatteUrl: String


    @Bean
    fun euxOAuthRestTemplate(): RestTemplate = opprettRestTemplate(euxUrl, "eux-credentials")

    @Bean
    fun euxKlientLib(): EuxKlientLib = EuxKlientLib(euxOAuthRestTemplate())

    @Bean
    fun norg2RestTemplate(): RestTemplate? = buildRestTemplate(norg2Url)

    @Bean
    fun journalpostOidcRestTemplate(): RestTemplate = opprettRestTemplateForJoark(joarkUrl,
        bearerTokenInterceptor(clientProperties("dokarkiv-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun fagmodulOidcRestTemplate(): RestTemplate = opprettRestTemplate(fagmodulUrl, "fagmodul-credentials")

    @Bean
    fun bestemSakOidcRestTemplate(): RestTemplate = opprettRestTemplate(bestemSakUrl, "pensjon-credentials")

    @Bean
    fun navansattRestTemplate(): RestTemplate? = opprettRestTemplate(navansattUrl, "navansatt-credentials")

    @Bean
    fun safGraphQlOidcRestTemplate() = opprettRestTemplateForJoark(graphQlUrl, bearerTokenInterceptor(
        clientProperties("saf-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun etterlatteRestTemplate() = opprettRestTemplate(etterlatteUrl, "etterlatte-credentials")

    /**
     * Denne bruker HttpComponentsClientHttpRequestFactory - angivelig for å fikse
     * problemer med HTTP-PATCH – som brukes mot joark.
     */
    private fun opprettRestTemplateForJoark(url: String, bearerTokenInterceptor: ClientHttpRequestInterceptor) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                bearerTokenInterceptor
            )
            .build().apply {
                requestFactory = HttpComponentsClientHttpRequestFactory()
            }
    }

    private fun opprettRestTemplate(url: String, oAuthKey: String) : RestTemplate {
        val constraints = StreamReadConstraints.builder()
            .maxStringLength(50_000_000)
            .build()

        val jsonFactory = JsonFactoryBuilder()
            .streamReadConstraints(constraints)
            .build()

        val objectMapper =  JsonMapper.builder(jsonFactory).build()

        val restTemplate = RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                bearerTokenInterceptor(clientProperties(oAuthKey), oAuth2AccessTokenService!!)
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }

        restTemplate.messageConverters
            .filter { it.javaClass.name.contains("Jackson") }
            .forEach { converter ->
                val method = converter.javaClass.getMethod("setObjectMapper", ObjectMapper::class.java)
                method.invoke(converter, objectMapper)
            }

        return restTemplate
    }

    private fun buildRestTemplate(url: String): RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestResponseLoggerInterceptor()
            )
            .build()
    }

    private fun clientProperties(oAuthKey: String): ClientProperties {
        return Optional.ofNullable(clientConfigurationProperties.registration[oAuthKey])
            .orElseThrow { RuntimeException("could not find oauth2 client config for example-onbehalfof") }
    }

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            val tokenChunks = response.access_token!!.split(".")
            val tokenBody =  tokenChunks[1]
            logger.debug("subject: " + JWTClaimsSet.parse(Base64.getDecoder().decode(tokenBody).decodeToString()).subject + "/n + $response.accessToken")

            request.headers.setBearerAuth(response.access_token!!)
            execution.execute(request, body!!)
        }
    }

}

