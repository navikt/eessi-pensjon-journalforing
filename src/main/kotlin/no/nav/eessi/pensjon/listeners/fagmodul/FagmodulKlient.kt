package no.nav.eessi.pensjon.listeners.fagmodul

import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class FagmodulKlient(private val fagmodulOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(FagmodulKlient::class.java) }

    fun hentPensjonSaklist(aktoerId: String): List<SakInformasjon> {
        val path = "/pensjon/sakliste/$aktoerId"

        val responseJson = try {
            val responsebody = fagmodulOidcRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java).body
            responsebody.orEmpty().also { logger.debug("Response body fra fagmodul: $it") }
        } catch(ex: Exception) {
            logger.error("En feil oppstod under henting av pensjonsakliste ex: $ex", ex)
            return emptyList()
        }

        // egen try catch for mapping av json der vi ønsker en exception og synlig feil i logging
        responseJson.let {
            return try {
                mapJsonToAny(responseJson)
            }
            catch(ex: Exception) {
                throw RuntimeException("En feil oppstod under mapping av json for pensjonsakliste: $ex")
            }
        }
    }
}
