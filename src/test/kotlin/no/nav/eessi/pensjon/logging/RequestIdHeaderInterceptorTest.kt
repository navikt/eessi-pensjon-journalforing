package no.nav.eessi.pensjon.logging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor.Companion.NAV_CALL_ID_HEADER
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor.Companion.REQUEST_ID_HEADER
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor.Companion.REQUEST_ID_MDC_KEY
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.http.client.MockClientHttpRequest

class RequestIdHeaderInterceptorTest {

    private val mockRequest = MockClientHttpRequest()
    private val mockExecution = mock<ClientHttpRequestExecution>()

    private val theRequestId = "THE REQUEST ID"
    private val aBody = "BODY".toByteArray()

    private val otherRequestId = "ANOTHER REQUEST ID"
    private val otherCallId = "ANOTHER CALL ID"

    @BeforeEach
    fun setup() {
        whenever(mockExecution.execute(any(), any())).thenReturn(mock())
    }

    @Test
    fun `should call downstream `() {
        RequestIdHeaderInterceptor().intercept(mockRequest, aBody, mockExecution)

        verify(mockExecution).execute(any(), any())
    }

    @Test
    fun `should propagate request-id from MDC`() {
        MDC.put(REQUEST_ID_MDC_KEY, theRequestId)

        RequestIdHeaderInterceptor().intercept(mockRequest, aBody, mockExecution)

        assertEquals(theRequestId, mockRequest.headers.getFirst(REQUEST_ID_HEADER))
        assertEquals(theRequestId, mockRequest.headers.getFirst(NAV_CALL_ID_HEADER))
    }

    @Test
    fun `should create request-id if none found on MDC`() {
        MDC.remove(REQUEST_ID_MDC_KEY)

        RequestIdHeaderInterceptor().intercept(mockRequest, aBody, mockExecution)

        assertNotNull(mockRequest.headers.getFirst(REQUEST_ID_HEADER))
        assertNotNull(mockRequest.headers.getFirst(NAV_CALL_ID_HEADER))
    }

    @Test
    fun `should add a value if someone else set it`() {
        MDC.put(REQUEST_ID_MDC_KEY, theRequestId)

        mockRequest.headers.add(REQUEST_ID_HEADER, otherRequestId)
        mockRequest.headers.add(NAV_CALL_ID_HEADER, otherCallId)

        RequestIdHeaderInterceptor().intercept(mockRequest, aBody, mockExecution)

        assertTrue(mockRequest.headers[REQUEST_ID_HEADER]!!.contains(theRequestId))
        assertTrue(mockRequest.headers[REQUEST_ID_HEADER]!!.contains(otherRequestId))
        assertTrue(mockRequest.headers[NAV_CALL_ID_HEADER]!!.contains(theRequestId))
        assertTrue(mockRequest.headers[NAV_CALL_ID_HEADER]!!.contains(otherCallId))
    }

    @AfterEach
    fun `cleanup MDC`() {
        MDC.remove(REQUEST_ID_MDC_KEY)
    }
}
