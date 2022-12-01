package no.nav.eessi.pensjon.shared.retry

import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class IOExceptionRetryInterceptor : ClientHttpRequestInterceptor {
    private val logger = LoggerFactory.getLogger(IOExceptionRetryInterceptor::class.java)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution) =
        withRetries { execution.execute(request, body) }

    private fun <T> withRetries(maxAttempts: Int = 3, waitTime: Long = 1L, timeUnit: TimeUnit = TimeUnit.SECONDS, func: () -> T): T {
        var failException: Throwable? = null
        var count = 0
        while (count < maxAttempts) {
            try {
                return func.invoke()
            } catch (ex: IOException) { // Dette bør ta seg av IOException - som typisk skjer der som det er nettverksissues.
                count++
                logger.warn("Attempt $count failed with ${ex.message} caused by ${ex.cause}")
                failException = ex
                Thread.sleep(timeUnit.toMillis(waitTime))
            }
        }
        logger.warn("Giving up after $count attempts.")
        throw failException!!
    }
}