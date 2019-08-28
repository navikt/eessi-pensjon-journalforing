package no.nav.eessi.pensjon.security.sts

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.client.support.BasicAuthenticationInterceptor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

/**
 * STS rest template for å hente OIDCtoken for nye tjenester
 *
 */
@Component
class STSRestTemplate {

    private val logger = LoggerFactory.getLogger(STSRestTemplate::class.java)

    @Value("\${srvusername}")
    lateinit var username: String

    @Value("\${srvpassword}")
    lateinit var password: String

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Bean
    fun securityTokenExchangeBasicAuthRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        logger.info("Oppretter RestTemplate for securityTokenExchangeBasicAuthRestTemplate")
        return templateBuilder
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        BasicAuthenticationInterceptor(username, password)
                ).build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}
