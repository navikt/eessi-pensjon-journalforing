package no.nav.eessi.pensjon.services.aktoerregister

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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

data class Identinfo(
        val ident: String,
        val identgruppe: String,
        val gjeldende: Boolean
)

data class IdentinfoForAktoer(
        val identer: List<Identinfo>?,
        val feilmelding: String?
)

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class AktoerregisterService(
        private val aktoerregisterRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(AktoerregisterService::class.java)

    @Value("\${app.name}")
    lateinit var appName: String

    fun hentGjeldendeNorskIdentForAkteorId(aktoerid: String): String {
        try {
            val response = doRequest(aktoerid, "NorskIdent")
            validateResponse(aktoerid, response)
            return response.getValue(aktoerid).identer!![0].ident
        } catch (ex: Exception) {
            logger.error("Feil ved henting av gjeldene norsk ident")
            throw ex
        }
    }

    fun hentGjeldendeAktoerIdForNorskIdent(norskIdent: String): String {
        try {
            val response = doRequest(norskIdent, "AktoerId")
            validateResponse(norskIdent, response)
            return response.getValue(norskIdent).identer!![0].ident
        } catch (ex: Exception) {
            logger.error("Feil ved henting av gjeldene aktoerId")
            throw ex
        }
    }

    private fun validateResponse(aktoerid: String, response: Map<String, IdentinfoForAktoer>) {
        if (response[aktoerid] == null)
            throw AktoerregisterIkkeFunnetException("Ingen identinfo for $aktoerid ble funnet")

        val identInfoForAktoer = response[aktoerid]!!

        if (identInfoForAktoer.feilmelding != null)
            throw AktoerregisterException(identInfoForAktoer.feilmelding)

        if (identInfoForAktoer.identer == null || identInfoForAktoer.identer.isEmpty())
            throw AktoerregisterIkkeFunnetException("Ingen identer returnert for $aktoerid")

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
        return metricsHelper.measure("aktoerregister") {
            val headers = HttpHeaders()
            headers["Nav-Personidenter"] = ident
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)

            val uriBuilder = UriComponentsBuilder.fromPath("/identer")
                    .queryParam("identgruppe", identGruppe)
                    .queryParam("gjeldende", gjeldende)
            logger.info("Kaller aktørregisteret: /identer")

            val responseEntity: ResponseEntity<String>
            try {
                responseEntity = aktoerregisterRestTemplate.exchange(uriBuilder.toUriString(),
                        HttpMethod.GET,
                        requestEntity,
                        String::class.java)

            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under kall til aktørregisteret ex: $ex body: ${ex.responseBodyAsString}")
                throw java.lang.RuntimeException("En feil oppstod under kall til aktørregisteret ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under kall til aktørregisteret ex: $ex")
                throw java.lang.RuntimeException("En feil oppstod under kall til aktørregisteret ex: ${ex.message}")
            }
            return@measure jacksonObjectMapper().readValue(responseEntity.body!!)
        }
    }
}

class AktoerregisterIkkeFunnetException(message: String?) : Exception(message)

class AktoerregisterException(message: String) : Exception(message)
