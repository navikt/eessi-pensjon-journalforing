//package no.nav.eessi.pensjon.journalforing.services.aktoerregister
//
//import no.nav.eessi.pensjon.journalforing.config.RequestResponseLoggerInterceptor
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.boot.web.client.RestTemplateBuilder
//import org.springframework.context.annotation.Bean
//import org.springframework.http.HttpRequest
//import org.springframework.http.MediaType
//import org.springframework.http.client.*
//import org.springframework.http.client.support.BasicAuthenticationInterceptor
//import org.springframework.stereotype.Component
//import org.springframework.web.client.RestTemplate
//import java.util.*
//
//
//@Component
//class AktoerregisterRestTemplate {
//
//
//    @Value("\${aktoerregister.api.v1.url}")
//    lateinit var url: String
//
//    @Value("\${srvusername}")
//    lateinit var username: String
//
//    @Value("\${srvpassword}")
//    lateinit var password: String
//
//
//    @Bean
//    fun aktoerregisterRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(url)
//                .additionalInterceptors(AktoerHeadersRequestInterceptor(),
//                        RequestResponseLoggerInterceptor(),
//                        BasicAuthenticationInterceptor(username, password)
//                )
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }
//
//    class AktoerHeadersRequestInterceptor : ClientHttpRequestInterceptor {
//        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
//            request.headers["X-Correlation-ID"] = UUID.randomUUID().toString()
//            request.headers["Content-Type"] = MediaType.APPLICATION_JSON.toString()
//            return execution.execute(request, body)
//        }
//    }
//}