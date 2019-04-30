package no.nav.eessi.pensjon.journalforing.config

import no.nav.security.oidc.context.OIDCRequestContextHolder
import no.nav.security.oidc.context.TokenContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse


private val logger = LoggerFactory.getLogger(OidcAuthorizationHeaderInterceptor::class.java)

class OidcAuthorizationHeaderInterceptor(private val oidcRequestContextHolder: OIDCRequestContextHolder) : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        logger.info("sjekker reqiest header for AUTH")
        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            val oidcToken = getIdTokenFromIssuer(oidcRequestContextHolder)
            logger.info("Adding Bearer-token to request: $oidcToken")
            request.headers[HttpHeaders.AUTHORIZATION] = "Bearer $oidcToken"
        }
        return execution.execute(request, body)
    }

    fun getIdTokenFromIssuer(oidcRequestContextHolder: OIDCRequestContextHolder): String {
        return getTokenContextFromIssuer(oidcRequestContextHolder).idToken
    }

    fun getTokenContextFromIssuer(oidcRequestContextHolder: OIDCRequestContextHolder): TokenContext {
        val context = oidcRequestContextHolder.oidcValidationContext
        if (context.issuers.isEmpty()) throw RuntimeException("No issuer found in context")
        // At this point more than one issuer is not supporteted. May be changed later.
        if (context.issuers.size > 1) throw RuntimeException("More than one issuer found in context. ")

        logger.debug("Returning token on : ${context.issuers.first()}")
        return context.getToken(context.issuers.first())
    }

}

class OidcAuthorizationHeaderInterceptorSelectIssuer(private val oidcRequestContextHolder: OIDCRequestContextHolder, private val issuer: String) : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        logger.info("sjekker reqiest header for AUTH")
        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            val oidcToken = getIdTokenFromSelectedIssuer(oidcRequestContextHolder, issuer)
            logger.info("Adding Bearer-token to request: $oidcToken")
            request.headers[HttpHeaders.AUTHORIZATION] = "Bearer $oidcToken"
        }
        return execution.execute(request, body)
    }

    fun getIdTokenFromSelectedIssuer(oidcRequestContextHolder: OIDCRequestContextHolder, issuer: String): String {
        return getTokenContextFromSelectedIssuer(oidcRequestContextHolder, issuer).idToken
    }

    fun getTokenContextFromSelectedIssuer(oidcRequestContextHolder: OIDCRequestContextHolder, issuer: String): TokenContext {
        val context = oidcRequestContextHolder.oidcValidationContext
        if (context.issuers.isEmpty()) throw RuntimeException("No issuer found in context")
        // At this point more than one, select one to use.
        logger.debug("Returning token on issuer: $issuer with token: ${context.getToken(issuer)}")
        return context.getToken(issuer)

    }
}




