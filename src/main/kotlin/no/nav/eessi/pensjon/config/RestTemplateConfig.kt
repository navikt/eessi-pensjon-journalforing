package no.nav.eessi.pensjon.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.security.sts.UsernameToOidcInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.*
import org.springframework.http.client.support.BasicAuthenticationInterceptor
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.util.*

@Configuration
class RestTemplateConfig(private val securityTokenExchangeService: STSService, private val meterRegistry: MeterRegistry) {

    @Value("\${aktoerregister.api.v1.url}")
    lateinit var aktoerregisterUrl: String

    @Value("\${oppgave.oppgaver.url}")
    lateinit var oppgaveUrl: String

    @Value("\${JOURNALPOST_V1_URL}")
    lateinit var joarkUrl: String

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var euxUrl: String

    @Value("\${EESSI_PENSJON_FAGMODUL_URL}")
    lateinit var fagmodulUrl: String

    @Value("\${NORG2_URL}")
    lateinit var norg2Url: String

    @Value("\${BestemSak_URL}")
    lateinit var bestemSakUrl: String

    @Value("\${srvusername}")
    lateinit var username: String

    @Value("\${srvpassword}")
    lateinit var password: String


    @Bean
    fun journalpostOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(joarkUrl)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                    FullRequestResponseLoggerInterceptor(),
                        RequestIdHeaderInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(HttpComponentsClientHttpRequestFactory()) // Trengs for å kjøre http-method: PATCH
                }
    }


    @Bean
    fun aktoerregisterRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(aktoerregisterUrl)
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        RequestResponseLoggerInterceptor(),
                        BasicAuthenticationInterceptor(username, password)
                )
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    @Bean
    fun oppgaveOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(oppgaveUrl)
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestInterceptor(),
                        RequestResponseLoggerInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        BasicAuthenticationInterceptor(username, password)
                )
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    @Bean
    fun fagmodulOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(fagmodulUrl)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    @Bean
    fun norg2OidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(norg2Url)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    @Bean
    fun bestemSakOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(bestemSakUrl)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    class RequestInterceptor : ClientHttpRequestInterceptor {
        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
            request.headers["X-Correlation-ID"] = UUID.randomUUID().toString()
            request.headers["Content-Type"] = MediaType.APPLICATION_JSON.toString()
            return execution.execute(request, body)
        }
    }
}



class FullRequestResponseLoggerInterceptor : ClientHttpRequestInterceptor {
    private val log: Logger by lazy { LoggerFactory.getLogger(RequestResponseLoggerInterceptor::class.java) }

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        logRequest(request, body)
        val response: ClientHttpResponse = execution.execute(request, body)
        logResponse(response)
        return response
    }

    private fun logRequest(request: HttpRequest, body: ByteArray) {
        if (log.isDebugEnabled) {
            val requestLog = StringBuffer()

            requestLog.append("\n===========================request begin================================================")
            requestLog.append("\nURI            :  ${request.uri}")
            requestLog.append("\nMethod         :  ${request.method}")
            requestLog.append("\nHeaders        :  ${request.headers}")
            requestLog.append("\nComplete body  :  ${String(body)}")
            requestLog.append("\n==========================request end================================================")
            log.debug(requestLog.toString())
        }
    }

    private fun logResponse(response: ClientHttpResponse) {
        if (log.isDebugEnabled) {
            val responseLog = StringBuilder()

            responseLog.append("\n===========================response begin================================================")
            responseLog.append("\nStatus code    : ${response.statusCode}")
            responseLog.append("\nStatus text    : ${response.statusText}")
            responseLog.append("\nHeaders        : ${response.headers}")
            responseLog.append("\nComplete body  :  ${String(response.body.readBytes())}")
            responseLog.append("\n==========================response end================================================")
            log.debug(responseLog.toString())
        }
    }
}

