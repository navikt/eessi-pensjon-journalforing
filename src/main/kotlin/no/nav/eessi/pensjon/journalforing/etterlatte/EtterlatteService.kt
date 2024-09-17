package no.nav.eessi.pensjon.journalforing.etterlatte

import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.Etterlatte
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
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

            when (response.statusCode) {
                HttpStatus.OK -> {
                    response.body?.let {
                        Result.success(mapJsonToAny<EtterlatteResponseData>(it))
                    } ?: Result.success(null) // Handle null body
                }
                HttpStatus.NOT_FOUND -> {
                    logger.warn("Ugyldig request; gjenny sak for ID: $sakId")
                    Result.failure(IllegalArgumentException("Sak ikke funnet(404) for sakId: $sakId"))
                }
                else -> {
                    logger.error("En generell feil  ${response.statusCode} under henting av gjenny sak for ID: $sakId")
                    Result.failure(IllegalStateException("Feil under henting av gjenny: ${response.statusCode}"))
                }
            }
        } catch (e: Exception) {
            logger.error("En generell feil oppstod under henting av gjenny sak: ${e.message}")
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