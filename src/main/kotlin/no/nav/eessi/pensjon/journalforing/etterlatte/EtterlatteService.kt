    package no.nav.eessi.pensjon.journalforing.etterlatte

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
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

    fun hentGjennySak(sakId: String): Result<EtterlatteResponseData?> {
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

            response.body?.let {
                Result.success(mapJsonToAny<EtterlatteResponseData>(it))
            } ?: Result.failure(IllegalArgumentException("Mangler melding fra gjenny")) // Handle null body
        } catch (e: HttpClientErrorException.NotFound) {
            Result.failure(IllegalArgumentException("Sak: $sakId ikke funnet (404)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class EtterlatteResponseData(
    val id: Int,
    val ident: String?,
    val enhet: String?,
    val sakType: GjennySakType?
)

enum class GjennySakType(val navn: String) {
    BARNEPENSJON("BARNEPENSJON"),
    OMSTILLINGSSTOENAD("OMSTILLINGSSTOENAD")
}