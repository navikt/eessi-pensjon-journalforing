package no.nav.eessi.pensjon.klienter.fagmodul

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct


/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class FagmodulKlient(
        private val fagmodulOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(FagmodulKlient::class.java) }

    private val hentYtelseTypeMapper = YtelseTypeMapper()

    private lateinit var hentYtelseKravtype: MetricsHelper.Metric
    private lateinit var hentFodselsdatoFraBuc: MetricsHelper.Metric
    private lateinit var hentSeds: MetricsHelper.Metric
    private lateinit var hentFnrFraBUC: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentYtelseKravtype = metricsHelper.init("hentYtelseKravtype")
        hentFodselsdatoFraBuc = metricsHelper.init("hentFodselsdatoFraBuc")
        hentSeds = metricsHelper.init("hentSeds")
        hentFnrFraBUC = metricsHelper.init("hentFnrFraBUC")
    }

    /**
     * Henter pin og ytelsetype , støttede SED typer:
     *  P2100 og P15000
     */
    fun hentYtelseKravType(rinaNr: String, dokumentId: String): String? {
        val ytelse = kallhentYtelseKravType(rinaNr, dokumentId)
        return hentYtelseTypeMapper.map(ytelse)
    }


    private fun kallhentYtelseKravType(rinaNr: String, dokumentId: String): HentYtelseTypeResponse? {
        return hentYtelseKravtype.measure {
            val path = "/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"
            return@measure try {
                logger.info("Henter ytelsetype for P_BUC_10 for rinaNr: $rinaNr , dokumentId: $dokumentId")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        HentYtelseTypeResponse::class.java).body
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av ytelsetype ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under henting av ytelsetype ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av ytelsetype ex: $ex")
                throw RuntimeException("En feil oppstod under henting av ytelsetype ex: ${ex.message}")
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
        return hentFodselsdatoFraBuc.measure {
            val path = "/sed/fodselsdato/$rinaNr/buctype/$buctype"
            try {
                logger.info("Henter fødselsdato for rinaNr: $rinaNr , buctype: $buctype")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av fødselsdato ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under henting av fødselsdato ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av fødselsdato ex: $ex")
                throw RuntimeException("En feil oppstod under henting av fødselsdato ex: ${ex.message}")
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
        val path = "/sed/fodselsnr/$rinaNr/buctype/$buctype"
        return hentFnrFraBUC.measure {
            try {
                logger.info("Henter fødselsdato for rinaNr: $rinaNr , buctype: $buctype")
                val fnr = fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java).body
                fnr
            } catch (ex: HttpClientErrorException) {
                // Ved ikke ikke treff på fnr fortsetter vi som om fnr ikke var utfylt
                if (ex.statusCode == HttpStatus.NOT_FOUND) {
                    logger.info("Fant ikke fnr i noen SEDer i BUC : $rinaNr")
                    return@measure null
                } else {
                    logger.error("En feil oppstod under henting av fødselsnummer fra buc ex: $ex body: ${ex.responseBodyAsString}")
                    throw RuntimeException("En feil oppstod under henting av fødselsnummer fra buc ex: ${ex.message} body: ${ex.responseBodyAsString}")
                }
            } catch (ex: HttpServerErrorException) {
                logger.error("En feil oppstod under henting av fødselsnummer fra buc ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under henting av fødselsnummer fra buc ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av fødselsnummer fra buc ex: $ex")
                throw RuntimeException("En feil oppstod under henting av fødselsnummer fra buc ex: ${ex.message}")
            }
        }
    }

    fun hentAlleDokumenter(rinaNr: String): String? {
        return hentSeds.measure {
            val path = "/buc/$rinaNr/allDocuments"
            return@measure try {
                logger.info("Henter jsondata for alle sed for rinaNr: $rinaNr")
                fagmodulOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        null,
                        String::class.java).body
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av alledokumenter ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under henting av alledokumenter ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under henting av alledokumenter ex: $ex")
                throw RuntimeException("En feil oppstod under henting av alledokumenter ex: ${ex.message}")
            }
        }
    }
}
