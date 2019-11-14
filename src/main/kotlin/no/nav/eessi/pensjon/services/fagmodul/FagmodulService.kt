package no.nav.eessi.pensjon.services.fagmodul

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class FagmodulService(
        private val fagmodulOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(FagmodulService::class.java) }

    /**
     * Henter pin og ytelsetype , støttede SED typer:
     *  P2100 og P15000
     */
    fun hentPinOgYtelseType(rinaNr: String, dokumentId: String): HentPinOgYtelseTypeResponse? {
        return metricsHelper.measure("hentYtelseKravtype") {
            val path = "/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"
            return@measure try {
                logger.info("Henter ytelsetype for P_BUC_10 for rinaNr: $rinaNr , dokumentId: $dokumentId")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        HentPinOgYtelseTypeResponse::class.java).body
            } catch (cex: HttpClientErrorException) {
                logger.error("En 4xx feil oppstod under henting av ytelsetype ex: ${cex.message} body: ${cex.responseBodyAsString}")
                throw RuntimeException("En 4xx feil oppstod under henting av ytelsetype ex: ${cex.message} body: ${cex.responseBodyAsString}")
            } catch (sex: HttpServerErrorException) {
                logger.error("En 5xx feil oppstod under henting av ytelsetype ex: ${sex.message} body: ${sex.responseBodyAsString}")
                throw RuntimeException("En 5xx feil oppstod under henting av ytelsetype ex: ${sex.message} body: ${sex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En ukjent feil oppstod under henting av ytelsetype ex: ${ex.message}")
                throw RuntimeException("En ukjent feil oppstod under henting av ytelsetype ex: ${ex.message}")
            }
        }
    }

    /**
     * Henter den forsikredes fødselsdato fra SEDer i den oppgitte BUCen
     *
     *  @param rinaNr BUC-id
     *  @param buctype BUC-type
     */
    fun hentFodselsdatoFraBuc(rinaNr: String, buctype: String) : String? {
        return metricsHelper.measure("hentSed") {
            val path = "/sed/fodselsdato/$rinaNr/buctype/$buctype"
            try {
                logger.info("Henter fødselsdato for rinaNr: $rinaNr , buctype: $buctype")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch (cex: HttpClientErrorException) {
                logger.error("En 4xx feil oppstod under henting av fødselsdato ex: ${cex.message} body: ${cex.responseBodyAsString}")
                throw RuntimeException("En 4xx feil oppstod under henting av fødselsdato ex: ${cex.message} body: ${cex.responseBodyAsString}")
            } catch (sex: HttpServerErrorException) {
                logger.error("En 5xx feil oppstod under henting av henting av fødselsdato ex: ${sex.message} body: ${sex.responseBodyAsString}")
                throw RuntimeException("En 5xx feil oppstod under henting av fødselsdato ex: ${sex.message} body: ${sex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En ukjent feil oppstod under henting av henting av fødselsdato ex: ${ex.message}")
                throw RuntimeException("En ukjent feil oppstod under henting av fødselsdato ex: ${ex.message}")
            }
        }
    }

    /**
     * Henter den forsikredes fødselsnummer fra SEDer i den oppgitte BUCen
     *
     *  @param rinaNr BUC-id
     *  @param buctype BUC-type
     */
    fun hentFnrFraBuc(rinaNr: String, buctype: String) : String? {
        return metricsHelper.measure("hentSed") {
            val path = "/sed/fodselsnr/$rinaNr/buctype/$buctype"
            try {
                logger.info("Henter fødselsdato for rinaNr: $rinaNr , buctype: $buctype")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch (cex: HttpClientErrorException) {
                logger.error("En 4xx feil oppstod under henting av fnr ex: ${cex.message} body: ${cex.responseBodyAsString}")
                throw RuntimeException("En 4xx feil oppstod under henting av fnr ex: ${cex.message} body: ${cex.responseBodyAsString}")
            } catch (sex: HttpServerErrorException) {
                logger.error("En 5xx feil oppstod under henting av fnr ex: ${sex.message} body: ${sex.responseBodyAsString}")
                throw RuntimeException("En 5xx feil oppstod under henting av fnr ex: ${sex.message} body: ${sex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En ukjent feil oppstod under henting av fnr ex: ${ex.message}")
                throw RuntimeException("En ukjent feil oppstod under henting av fnr ex: ${ex.message}")
            }
        }
    }

    fun hentAlleDokumenterFraRinaSak(rinaNr: String): String? {
        return metricsHelper.measure("hentSeds") {
            val path = "/buc/$rinaNr/allDocuments"
            return@measure try {
                logger.info("Henter jsondata for alle sed for rinaNr: $rinaNr")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        null,
                        String::class.java).body
            } catch (cex: HttpClientErrorException) {
                logger.error("En 4xx feil oppstod under henting av av SEDer ex: ${cex.message} body: ${cex.responseBodyAsString}")
                throw RuntimeException("En 4xx feil oppstod under henting av SEDer ex: ${cex.message} body: ${cex.responseBodyAsString}")
            } catch (sex: HttpServerErrorException) {
                logger.error("En 5xx feil oppstod under henting av henting av SEDer ex: ${sex.message} body: ${sex.responseBodyAsString}")
                throw RuntimeException("En 5xx feil oppstod under henting av SEDer ex: ${sex.message} body: ${sex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En ukjent feil oppstod under henting av henting av SEDer ex: ${ex.message}")
                throw RuntimeException("En ukjent feil oppstod under henting av SEDer ex: ${ex.message}")
            }
        }
    }
}
