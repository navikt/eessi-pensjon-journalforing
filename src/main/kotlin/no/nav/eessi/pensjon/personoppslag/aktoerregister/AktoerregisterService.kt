package no.nav.eessi.pensjon.personoppslag.aktoerregister

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*
import javax.annotation.PostConstruct

data class Identinfo(
        val ident: String,
        val identgruppe: String,
        val gjeldende: Boolean
)

data class IdentinfoForAktoer(
        val identer: List<Identinfo>?,
        val feilmelding: String?
)

@Service
class AktoerregisterService(
        private val aktoerregisterRestTemplate: RestTemplate,
        @Value("\${NAIS_APP_NAME}") private val appName: String,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(AktoerregisterService::class.java)

    private lateinit var AktoerNorskIdentForAktorId: MetricsHelper.Metric
    private lateinit var AktoerforNorskIdent: MetricsHelper.Metric
    private lateinit var AktoerRequester: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        AktoerNorskIdentForAktorId = metricsHelper.init("AktoerNorskIdentForAktorId")
        AktoerforNorskIdent = metricsHelper.init("AktoerforNorskIdent")
        AktoerRequester = metricsHelper.init("AktoerRequester")
    }

    fun hentGjeldendeNorskIdentForAktorId(aktorid: String?): String {
        return AktoerNorskIdentForAktorId.measure {
            if (aktorid.isNullOrBlank()) throw ManglerAktoerIdException("Mangler AktoerId")

            val response = doRequest(aktorid, "NorskIdent")
            validateResponse(aktorid, response)

            response[aktorid]?.identer!![0].ident
        }
    }

    fun hentGjeldendeAktorIdForNorskIdent(norskIdent: String?): String {
        return AktoerforNorskIdent.measure {
            if (norskIdent.isNullOrBlank()) throw ManglerAktoerIdException("Mangler fnr/ident")

                val response = doRequest(norskIdent, "AktoerId")
                validateResponse(norskIdent, response)

            response[norskIdent]?.identer!![0].ident
        }
    }

    private fun validateResponse(aktorid: String, response: Map<String, IdentinfoForAktoer>) {
        if (response[aktorid] == null)
            throw AktoerregisterIkkeFunnetException("Ingen identinfo for $aktorid ble funnet")

        val identInfoForAktoer = response[aktorid]!!

        if (identInfoForAktoer.feilmelding != null)
            throw AktoerregisterException(identInfoForAktoer.feilmelding)

        if (identInfoForAktoer.identer == null || identInfoForAktoer.identer.isEmpty())
            throw AktoerregisterIkkeFunnetException("Ingen identer returnert for $aktorid")

        if (identInfoForAktoer.identer.size > 1) {
            logger.info("Identer returnert fra aktoerregisteret:")
            identInfoForAktoer.identer.forEach {
                logger.info("ident: ${it.ident}, gjeldende: ${it.gjeldende}, identgruppe: ${it.identgruppe}")
            }
            throw AktoerregisterException("Forventet 1 ident, fant ${identInfoForAktoer.identer.size}")
        }
    }

    private fun doRequest(ident: String,
                          identGruppe: String,
                          gjeldende: Boolean = true): Map<String, IdentinfoForAktoer> {

        return AktoerRequester.measure {
            val headers = HttpHeaders()
            headers["Nav-Personidenter"] = ident
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)

            val uriBuilder = UriComponentsBuilder.fromPath("/identer")
                    .queryParam("identgruppe", identGruppe)
                    .queryParam("gjeldende", gjeldende)
            logger.info("Kaller aktørregisteret: /identer")

            val responseEntity: ResponseEntity<String>?
            return@measure try {
                responseEntity = aktoerregisterRestTemplate.exchange(uriBuilder.toUriString(),
                        HttpMethod.GET,
                        requestEntity,
                        String::class.java)
                jacksonObjectMapper().readValue(responseEntity.body!!)

            } catch (hcee: HttpClientErrorException) {
                val errorBody = hcee.responseBodyAsString
                logger.error("Aktørregister feiler med HttpClientError body: $errorBody", hcee)
                throw AktoerregisterException("Received ${hcee.statusCode} ${hcee.statusCode.reasonPhrase} from aktørregisteret")
            } catch (hsee: HttpServerErrorException) {
                val errorBody = hsee.responseBodyAsString
                logger.error("Aktørregisteret feiler med HttpServerError body: $errorBody", hsee)
                throw AktoerregisterException("Received ${hsee.statusCode} ${hsee.statusCode.reasonPhrase} from aktørregisteret")
            } catch(ex: Exception) {
                logger.error(ex.message, ex)
                throw AktoerregisterException(ex.message!!)
            }
        }
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class AktoerregisterIkkeFunnetException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class AktoerregisterException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class ManglerAktoerIdException(message: String) : Exception(message)
