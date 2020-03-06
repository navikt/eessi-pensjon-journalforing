package no.nav.eessi.pensjon.security.sts

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse


class UsernameToOidcInterceptor(private val securityTokenExchangeKlient: STSKlient) : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val token = securityTokenExchangeKlient.getSystemOidcToken()
        request.headers[HttpHeaders.AUTHORIZATION] = "Bearer $token"
        return execution.execute(request, body)
    }
}
