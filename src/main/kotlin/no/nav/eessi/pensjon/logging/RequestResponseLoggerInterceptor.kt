package no.nav.eessi.pensjon.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class RequestResponseLoggerInterceptor : ClientHttpRequestInterceptor {

    private val log: Logger by lazy { LoggerFactory.getLogger(RequestResponseLoggerInterceptor::class.java) }

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        logRequest(request, body)
        val response: ClientHttpResponse = execution.execute(request, body)
        logResponse(response, body)
        return response
    }

    private fun logRequest(request: HttpRequest, body: ByteArray) {
        if (log.isDebugEnabled) {
            val requestLog = StringBuffer()

            requestLog.append("\n===========================request begin================================================\n")
            requestLog.append("URI            :  ${request.uri}  \n")
            requestLog.append("Method         :  ${request.method} \n")
            requestLog.append("Headers        :  ${request.headers} \n")
            requestLog.append(trunkerBodyHvisDenErStor(body))
            requestLog.append("==========================request end================================================")
            log.debug(requestLog.toString())
        }
    }

    private fun logResponse(response: ClientHttpResponse, body: ByteArray) {
        if (log.isDebugEnabled) {
            val responseLog = StringBuilder()

            responseLog.append("\n===========================response begin================================================\n")
            responseLog.append("Status code    : ${response.statusCode} \n")
            responseLog.append("Status text    : ${response.statusText} \n")
            responseLog.append("Headers        : ${response.headers} \n")
            responseLog.append(trunkerBodyHvisDenErStor(body))
            responseLog.append("==========================response end================================================")
            log.debug(responseLog.toString())
        }
    }

    private fun trunkerBodyHvisDenErStor(body: ByteArray) : String {
        // Korter ned body dersom den er veldig stor ( ofte ved binærinnhold )
        return if (body.size > 5000) {
            "Truncated body :  ${String(body.copyOfRange(0, 5000))} \n"
        } else {
            "Complete body  :  ${String(body)} \n"
        }
    }
}
