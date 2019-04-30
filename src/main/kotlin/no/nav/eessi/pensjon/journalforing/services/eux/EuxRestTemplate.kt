package no.nav.eessi.pensjon.journalforing.services.eux

import no.nav.eessi.pensjon.journalforing.config.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.journalforing.services.sts.STSService
import no.nav.eessi.pensjon.journalforing.services.sts.UsernameToOidcInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate

@Component
class EuxRestTemplate(val securityTokenExchangeService: STSService) {

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var url: String

    @Bean
    fun euxOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(url)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(RequestResponseLoggerInterceptor(),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}