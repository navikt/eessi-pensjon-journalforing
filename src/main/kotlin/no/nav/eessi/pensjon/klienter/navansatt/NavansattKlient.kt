package no.nav.eessi.pensjon.klienter.navansatt

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NavansattKlient(private val navansattRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(NavansattKlient::class.java) }

    fun hentAnsatt(saksbehandler: String): String? {
        val path = "/navansatt/$saksbehandler"
        try {
            val json = navansattRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java
            ).body.orEmpty()
            return json
        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av saksbehandler fra navansatt ex: $ex", ex)
        }
        return null
    }

    fun hentAnsattEnhet(saksbehandler: String): String? {
        val path = "/navansatt/$saksbehandler/enhet"
        try {
            val responsebody = navansattRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java
            ).body
            return responsebody.orEmpty()
        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av enhet for saksbehandler ex: $ex", ex)
        }
        return null
    }
}