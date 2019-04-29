package no.nav.eessi.eessifagmodul.services.sts

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse


class UsernameToOidcInterceptor(private val securityTokenExchangeService: STSService) : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val token = securityTokenExchangeService.getSystemOidcToken()
        request.headers[HttpHeaders.AUTHORIZATION] = "Bearer $token"
        return execution.execute(request, body)
    }
}
