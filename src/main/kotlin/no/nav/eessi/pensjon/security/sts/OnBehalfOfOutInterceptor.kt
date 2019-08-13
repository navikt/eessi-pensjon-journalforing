package no.nav.eessi.pensjon.security.sts

/**
 * Documentation at https://confluence.adeo.no/display/KES/STS+-+Brukerdokumentasjon
 * Example implementation at http://stash.devillo.no/projects/KES/repos/eksempelapp-token/browse
 */

import org.apache.cxf.message.Message
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.apache.cxf.phase.Phase
import org.apache.cxf.ws.security.SecurityConstants
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.*
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

const val REQUEST_CONTEXT_ONBEHALFOF_TOKEN = "request.onbehalfof.token"
const val REQUEST_CONTEXT_ONBEHALFOF_TOKEN_TYPE = "request.onbehalfof.tokentype"

class OnBehalfOfOutInterceptor : AbstractPhaseInterceptor<Message>(Phase.SETUP) {

    enum class TokenType constructor(val valueType: String) {
        OIDC("urn:ietf:params:oauth:token-type:jwt")
    }

    override fun handleMessage(message: Message) {
        val token: String? = message[REQUEST_CONTEXT_ONBEHALFOF_TOKEN] as String
        val tokenType: TokenType? = message[REQUEST_CONTEXT_ONBEHALFOF_TOKEN_TYPE] as TokenType

        if (token == null || tokenType == null)
            throw RuntimeException("could not find OnBehalfOfToken token in requestcontext with key $REQUEST_CONTEXT_ONBEHALFOF_TOKEN")

        val tokenBytes = token.toByteArray()
        val wrappedToken = wrapTokenForTransport(tokenBytes, tokenType)
        message[SecurityConstants.STS_TOKEN_ON_BEHALF_OF] = createOnBehalfOfElement(wrappedToken)
    }

    private fun createOnBehalfOfElement(wrappedToken: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)

        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(wrappedToken)))
        return document.documentElement
    }

    private fun wrapTokenForTransport(token: ByteArray, tokenType: TokenType): String {
        val base64encodedToken = Base64.getEncoder().encodeToString(token)
        return ("<wsse:BinarySecurityToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\""
                + " EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\""
                + " ValueType=\"" + tokenType.valueType + "\" >" + base64encodedToken + "</wsse:BinarySecurityToken>")
    }
}
