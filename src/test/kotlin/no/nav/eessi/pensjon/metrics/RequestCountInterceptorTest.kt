package no.nav.eessi.pensjon.metrics

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.core.instrument.search.Search
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.io.IOException
import java.net.URI
import java.util.stream.Collectors

class RequestCountInterceptorTest {

    private val someUri = URI("/abc")
    private val httpGet = HttpMethod.GET
    private val httpPost = HttpMethod.POST

    private val mockRequest = MockClientHttpRequest()
    private val mockExecution = mock<ClientHttpRequestExecution>()
    private val meterRegistry = SimpleMeterRegistry()
    private val requestCountInterceptor = RequestCountInterceptor(meterRegistry)

    private val aBody = "BODY".toByteArray()
    private val responseBody = "RESPONSE BODY".toByteArray()

    @Before
    fun setup() {
    }

    @After
    fun `should always call downstream`() {
        verify(mockExecution).execute(any(), any())
    }

    @Test
    fun `should call downstream in normal case`() {
        whenever(mockExecution.execute(any(), any())).thenReturn(mock())
        requestCountInterceptor.intercept(mockRequest, aBody, mockExecution)
        verify(mockExecution).execute(any(), any())
    }

    @Test
    fun `should count successful call`() {
        mockRequest.method = httpGet
        mockRequest.uri = someUri

        whenever(mockExecution.execute(any(), any())).thenReturn(MockClientHttpResponse(responseBody, HttpStatus.OK))

        requestCountInterceptor.intercept(mockRequest, aBody, mockExecution)

        assertCount(1, httpGet.name, someUri.toString(), RequestCountInterceptor.SUCCESS_VALUE, 200, "none")
    }

    @Test
    fun `should count failed call`() {
        mockRequest.method = httpPost
        mockRequest.uri = someUri

        whenever(mockExecution.execute(any(), any())).thenReturn(MockClientHttpResponse(responseBody, HttpStatus.BAD_REQUEST))

        requestCountInterceptor.intercept(mockRequest, aBody, mockExecution)

        assertCount(1, httpPost.name, someUri.toString(), RequestCountInterceptor.FAILURE_VALUE, 400, RequestCountInterceptor.UNKNOWN_EXCEPTION_TAG_VALUE)
    }

    @Test
    fun `should count IOException call`() {
        mockRequest.method = httpPost
        mockRequest.uri = someUri

        whenever(mockExecution.execute(any(), any())).thenThrow(IOException())

        try {
            requestCountInterceptor.intercept(mockRequest, aBody, mockExecution)
            fail("should propagate exception")
        } catch (ex: IOException) {
            // expected
        }

        assertCount(1, httpPost.name, someUri.toString(), RequestCountInterceptor.FAILURE_VALUE, RequestCountInterceptor.UNKNOWN_STATUS_TAG_VALUE, "IOException")
    }

    private fun assertCount(expectedCount: Int, httpMethod: String, uri: String, type: String, status: Int, exception: String) {
        val counterToCheck = meterRegistry.counter(
                RequestCountInterceptor.COUNTER_METER_NAME,
                RequestCountInterceptor.HTTP_METHOD_TAG, httpMethod,
                RequestCountInterceptor.URI_TAG, uri,
                RequestCountInterceptor.TYPE_TAG, type,
                RequestCountInterceptor.STATUS_TAG, status.toString(),
                RequestCountInterceptor.EXCEPTION_TAG, exception)

        assertEquals("\nexpected: ${meterName(counterToCheck.id) }: $expectedCount, but found:\n${counterList(meterRegistry)}",
                expectedCount.toDouble(),
                counterToCheck.count(),
                0.1)
    }

    private fun counterList(meterRegistry: MeterRegistry) =
            Search.`in`(meterRegistry).counters().map { "${meterName(it.id) } : ${it.count()}" }.joinToString("\n")

    private fun meterName(id: Meter.Id) =
            id.getConventionName(NamingConvention.snakeCase) + id.getConventionTags(NamingConvention.snakeCase).stream().map { tag -> "{${tag.key}=\\\"${tag.value}\\\"}" }.collect(Collectors.joining())

}
