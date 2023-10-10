package no.nav.eessi.pensjon.klienter.navansatt

import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NavansattKlient(private val navansattRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(NavansattKlient::class.java) }

    fun hentAnsattEnhet(saksbehandler: String): String{
        val path = "/navansatt/$saksbehandler"
        try {
            val responsebody = navansattRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java
            ).body
            val json = responsebody.orEmpty()
            return mapJsonToAny(json)
        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av pensjonsakliste ex: $ex", ex)
            throw RuntimeException("En feil oppstod under henting av pensjonsakliste ex: ${ex.message}")
        }
    }
}