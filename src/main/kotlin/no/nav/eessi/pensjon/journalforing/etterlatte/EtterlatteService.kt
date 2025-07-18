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
import java.time.LocalDate
import java.time.LocalDateTime

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

            logger.info("Hent sak fra gjenny: response: ${response.body}".trimMargin())

            response.body?.let {
                Result.success(mapJsonToAny<EtterlatteResponseData>(it))
            } ?: Result.failure(IllegalArgumentException("Mangler melding fra gjenny")) // Handle null body
        } catch (e: HttpClientErrorException.NotFound) {
            Result.failure(IllegalArgumentException("Sak: $sakId ikke funnet (404)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hentGjennyVedtak(fnr: String): Result<EtterlatteVedtakResponseData?> {
        val url = "/api/v1/vedtak"
        logger.debug("Henter informasjon fra gjenny: $url")
        val json = """
                "foedselsnummer": "string"
            """.trimIndent()

        return try {
            val response = etterlatteRestTemplate.exchange(
                url,
                HttpMethod.POST,
                HttpEntity(json, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                String::class.java
            )

            logger.info("Hent sak fra gjenny: response: ${response.body}".trimMargin())

            response.body?.let {
                Result.success(mapJsonToAny<EtterlatteVedtakResponseData>(it))
            } ?: Result.failure(IllegalArgumentException("Mangler melding fra gjenny")) // Handle null body
        } catch (_: HttpClientErrorException.NotFound) {
            Result.failure(IllegalArgumentException("Vedtak ikke funnet (404)"))
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

data class EtterlatteVedtakResponseData(
    val vedtak: List<GjennyVedtak>
)

data class GjennyVedtak(
    val sakId: Int,
    val sakType: String,
    val virkningstidspunkt: LocalDate,
    val type: String,
    val utbetaling: List<GjennyUtbetaling>,
    val iverksettelsesTidspunkt: LocalDateTime,
)

data class GjennyUtbetaling(
  val fraOgMed : LocalDate,
  val tilOgMed : LocalDate,
  val beloep : String,
)



enum class GjennySakType(val navn: String) {
    BARNEPENSJON("BARNEPENSJON"),
    OMSTILLINGSSTOENAD("OMSTILLINGSSTOENAD")
}