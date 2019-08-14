package no.nav.eessi.pensjon.services.fagmodul

import io.micrometer.core.instrument.Metrics.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException

@Service
class FagmodulService(private val fagmodulOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(FagmodulService::class.java) }

    private val hentYtelsetypeVellykkede = counter("eessipensjon_journalforing", "http_request", "hentYtelseKravtype", "type", "vellykkede")
    private val hentYtelsetypeFeilede = counter("eessipensjon_journalforing", "http_request", "hentYtelseKravtype", "type", "feilede")

    fun hentPinOgYtelseType(rinaNr: String, dokumentId: String): HentPinOgYtelseTypeResponse {

        val path = "/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"
        try {
            logger.info("Henter ytelsetype for P_BUC_10 for rinaNr: $rinaNr , dokumentId: $dokumentId")
            val response = fagmodulOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity(""),
                    HentPinOgYtelseTypeResponse::class.java)
            if (!response.statusCode.isError) {
                logger.info("Hentet ytelsetype for P_BUC_10 fra fagmodulen: ${response.body}")
                hentYtelsetypeVellykkede.increment()
                return response.body!!
            } else {
                hentYtelsetypeFeilede.increment()
                throw RuntimeException("Noe gikk galt under henting av Ytelsetype fra fagmodulen: ${response.statusCode}")
            }
        } catch (ex: Exception) {
            hentYtelsetypeFeilede.increment()
            logger.error("Noe gikk galt under henting av ytelsetype fra fagmodulen: ${ex.message}")
            throw RuntimeException("Feil ved henting av ytelsetype fra fagmodulen")
        }
    }
}
