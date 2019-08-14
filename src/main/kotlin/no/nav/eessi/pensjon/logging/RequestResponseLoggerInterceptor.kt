package no.nav.eessi.pensjon.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import java.nio.charset.Charset

class RequestResponseLoggerInterceptor : ClientHttpRequestInterceptor {

    private val log: Logger by lazy { LoggerFactory.getLogger(RequestResponseLoggerInterceptor::class.java) }

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        logRequest(request, body)
        val response: ClientHttpResponse = execution.execute(request, body)
        logResponse(response)
        return response
    }

    private fun logRequest(request: HttpRequest, body: ByteArray) {
        if (log.isDebugEnabled) {
            log.debug("\n===========================request begin================================================\n"
                    + "URI         : " + request.uri + "\n"
                    + "Method      : " + request.method + "\n"
                    + "Headers     : " + request.headers + "\n"
                    + "Request body: " + String(body) + "\n"
                    + "==========================request end================================================")
        }
    }

    private fun logResponse(response: ClientHttpResponse) {
        if (log.isDebugEnabled) {
            log.debug("\n===========================response begin================================================\n"
                    + "Status code  : " + response.statusCode + "\n"
                    + "Status text  : " + response.statusText + "\n"
                    + "Headers      : " + response.headers + "\n"
                    + "Response body: " + StreamUtils.copyToString(response.body, Charset.defaultCharset()) + "\n"
                    + "==========================response end================================================")
        }
    }
}
