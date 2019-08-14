package no.nav.eessi.pensjon.services.aktoerregister

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.Metrics.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
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

@Service
class AktoerregisterService(private val aktoerregisterRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(AktoerregisterService::class.java)

    private val aktoerregisterVellykkede = counter("eessipensjon_journalforing", "http_request", "aktoerregister", "type", "vellykkede")
    private val aktoerregisterFeilede = counter("eessipensjon_journalforing", "http_request", "aktoerregister", "type", "feilede")

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
        val headers = HttpHeaders()
        headers["Nav-Personidenter"] = ident
        headers["Nav-Consumer-Id"] = appName
        headers["Nav-Call-Id"] = UUID.randomUUID().toString()
        val requestEntity = HttpEntity<String>(headers)

        val uriBuilder = UriComponentsBuilder.fromPath("/identer")
                .queryParam("identgruppe", identGruppe)
                .queryParam("gjeldende", gjeldende)
        logger.info("Kaller aktørregisteret: /identer")
        val responseEntity = aktoerregisterRestTemplate.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                String::class.java)

        if (responseEntity.statusCode.isError) {
            logger.error("Fikk ${responseEntity.statusCode} feil fra aktørregisteret")
            aktoerregisterFeilede.increment()
            if (responseEntity.hasBody()) {
                logger.error(responseEntity.body.toString())
            }
            throw AktoerregisterException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from aktørregisteret")
        }
        aktoerregisterVellykkede.increment()

        return jacksonObjectMapper().readValue(responseEntity.body!!)
    }
}

class AktoerregisterIkkeFunnetException(message: String?) : Exception(message)

class AktoerregisterException(message: String) : Exception(message)
