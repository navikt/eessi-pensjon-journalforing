package no.nav.eessi.pensjon.journalforing.services.fagmodul

import no.nav.eessi.pensjon.journalforing.metrics.counter
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
    private final val hentYtelsetypeTellerNavn = "eessipensjon_journalforing.hentpdf"
    private val hentYtelsetypeVellykkede = counter(hentYtelsetypeTellerNavn, "vellykkede")
    private val hentYtelsetypeFeilede = counter(hentYtelsetypeTellerNavn, "feilede")

    fun hentYtelseTypeForPBuc10(rinaNr: String, dokumentId: String): HentYtelseTypeResponse {

        val path = "/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"
        try {
            logger.info("Henter ytelsetype for P_BUC_10 for rinaNr: $rinaNr , dokumentId: $dokumentId")
            val response = fagmodulOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity(""),
                    HentYtelseTypeResponse::class.java)
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
