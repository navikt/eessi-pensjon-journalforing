package no.nav.eessi.pensjon.klienter.fagmodul

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.SakInformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class FagmodulKlient(private val fagmodulOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(FagmodulKlient::class.java) }

    fun hentPensjonSaklist(aktoerId: String): List<SakInformasjon> {
        val path = "/pensjon/sakliste/$aktoerId"
        try {
            val responsebody = fagmodulOidcRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java).body
            val json = responsebody.orEmpty()
            return mapJsonToAny(json, typeRefs<List<SakInformasjon>>())
        } catch(ex: HttpStatusCodeException) {
            logger.error("En feil oppstod under henting av pensjonsakliste ex: $ex body: ${ex.responseBodyAsString}", ex)
            throw RuntimeException("En feil oppstod under henting av pensjonsakliste ex: ${ex.message} body: ${ex.responseBodyAsString}")
        } catch(ex: Exception) {
            logger.error("En feil oppstod under henting av pensjonsakliste ex: $ex", ex)
            throw RuntimeException("En feil oppstod under henting av pensjonsakliste ex: ${ex.message}")
        }
    }

}
