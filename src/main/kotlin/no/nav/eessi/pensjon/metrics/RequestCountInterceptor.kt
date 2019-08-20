package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

class RequestCountInterceptor(private val meterRegistry: MeterRegistry) : ClientHttpRequestInterceptor {

    companion object {
        const val COUNTER_METER_NAME = "outgoing_call"
        const val UNKNOWN_STATUS_TAG_VALUE = 500

        const val TYPE_TAG = "type"
        const val STATUS_TAG = "status"
        const val EXCEPTION_TAG = "exception"
        const val HTTP_METHOD_TAG = "method"
        const val URI_TAG = "uri"

        const val SUCCESS_VALUE = "successful"
        const val FAILURE_VALUE = "failed"
        const val NO_EXCEPTION_TAG_VALUE = "none"
        const val UNKNOWN_EXCEPTION_TAG_VALUE = "unknown"
    }

    @Throws(IOException::class)
    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        var exception = NO_EXCEPTION_TAG_VALUE
        var response: ClientHttpResponse? = null

        return try {
            response = execution.execute(request, body)
            response
        } catch (e: Throwable) {
            exception = e.javaClass.simpleName
            throw e
        } finally {
            meterRegistry.counter(COUNTER_METER_NAME,
                    HTTP_METHOD_TAG, request.methodValue,
                    URI_TAG, request.uri.toString(),
                    TYPE_TAG, if (response != null && response.rawStatusCode < 400) SUCCESS_VALUE else FAILURE_VALUE,
                    STATUS_TAG, if (exception == NO_EXCEPTION_TAG_VALUE && response != null) response.rawStatusCode.toString() else UNKNOWN_STATUS_TAG_VALUE.toString(),
                    EXCEPTION_TAG, if (exception == NO_EXCEPTION_TAG_VALUE && (response == null || response.rawStatusCode >= 400)) UNKNOWN_EXCEPTION_TAG_VALUE else exception)
                    .increment()
        }
    }
}
