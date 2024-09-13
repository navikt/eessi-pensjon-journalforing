package no.nav.eessi.pensjon.journalforing.etterlatte

import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class EtterlatteService(

    private val etterlatteRestTemplate: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(EtterlatteService::class.java)

    private lateinit var henterSakFraEtterlatte: MetricsHelper.Metric

    init {
        henterSakFraEtterlatte = metricsHelper.init("henterSakFraEtterlatte")
    }

    fun hentGjennySak(sakId: String): String? {

        val url = "/api/sak/$sakId"
        logger.debug("Henter informasjon fra gjenny: $url")

        return try {

            val response = etterlatteRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                String::class.java
            )

            logger.debug("Hent sak fra gjenny: response: ${response.body}".trimMargin())

            response.body
        } catch (e: Exception) {
            logger.error("En generell feil oppstod under henting av gjenny sak: ${e.message}")
            return null
            //throw RuntimeException("Feil ved henting av gjenny sak", e)
        }
    }
}