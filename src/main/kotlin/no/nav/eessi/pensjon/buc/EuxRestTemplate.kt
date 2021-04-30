package no.nav.eessi.pensjon.buc

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.security.sts.UsernameToOidcInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class EuxRestTemplate(
    private val registry: MeterRegistry,
    private val stsService: STSService
) {

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var url: String

    @Bean
    fun euxUsernameOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestCountInterceptor(registry),
                RequestResponseLoggerInterceptor(),
                UsernameToOidcInterceptor(stsService))
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }

}
