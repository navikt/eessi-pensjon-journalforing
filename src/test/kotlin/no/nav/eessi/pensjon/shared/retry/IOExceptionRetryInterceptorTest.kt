package no.nav.eessi.pensjon.shared.retry

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit

internal class IOExceptionRetryInterceptorTest {

    private val interceptor = IOExceptionRetryInterceptor()

    @Test
    fun `intercept returns response on first successful attempt`() {
        val request = mockk<HttpRequest>()
        val body = byteArrayOf(1, 2, 3)
        val execution = mockk<ClientHttpRequestExecution>()
        val response = mockk<ClientHttpResponse>()

        every { execution.execute(request, body) } returns response

        val result = interceptor.intercept(request, body, execution)

        assertSame(response, result)
        verify(exactly = 1) { execution.execute(request, body) }
    }

    @Test
    fun `withRetries retries IOExceptions until success`() {
        var attempts = 0

        val result = invokeWithRetries(maxAttempts = 3) {
            attempts++
            if (attempts < 3) throw IOException("temporary failure")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `withRetries throws last IOException after max attempts`() {
        var attempts = 0

        val exception = assertThrows(InvocationTargetException::class.java) {
            invokeWithRetries(maxAttempts = 2) {
                attempts++
                throw IOException("still failing")
            }
        }

        assertTrue(exception.cause is IOException)
        assertEquals("still failing", exception.cause?.message)
        assertEquals(2, attempts)
    }

    @Test
    fun `withRetries does not retry non IOExceptions`() {
        var attempts = 0

        val exception = assertThrows(InvocationTargetException::class.java) {
            invokeWithRetries(maxAttempts = 3) {
                attempts++
                throw IllegalStateException("stop")
            }
        }

        assertTrue(exception.cause is IllegalStateException)
        assertEquals("stop", exception.cause?.message)
        assertEquals(1, attempts)
    }

    private fun <T> invokeWithRetries(
        maxAttempts: Int,
        waitTime: Long = 0,
        timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
        func: () -> T
    ): T {
        val method = IOExceptionRetryInterceptor::class.java.getDeclaredMethod(
            "withRetries",
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            TimeUnit::class.java,
            Function0::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        return method.invoke(interceptor, maxAttempts, waitTime, timeUnit, func) as T
    }
}
