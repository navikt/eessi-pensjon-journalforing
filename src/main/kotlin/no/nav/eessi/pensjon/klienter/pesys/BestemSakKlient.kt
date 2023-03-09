package no.nav.eessi.pensjon.klienter.pesys

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.util.*

@Component
class BestemSakKlient(private val bestemSakOidcRestTemplate: RestTemplate,
                      @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(BestemSakKlient::class.java) }
    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

    private lateinit var hentPesysSaker: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentPesysSaker = metricsHelper.init("hentPesysSaker")
    }

    /**
     * Henter pesys sakID for en gitt aktørID og saktype
     *
     * Foreløbig er alder, uføre og gjenlevende støttet i her men tjenesten støtter alle typer
     */
    fun kallBestemSak(requestBody: BestemSakRequest): BestemSakResponse? {
        return hentPesysSaker.measure {
            return@measure try {
                logger.info("Kaller bestemSak i PESYS")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                val response = bestemSakOidcRestTemplate.exchange(
                        "/",
                        HttpMethod.POST,
                        HttpEntity(requestBody.toJson(), headers),
                        String::class.java)

                mapper.readValue(response.body, BestemSakResponse::class.java)
            } catch (ex: HttpStatusCodeException) {
                throw RuntimeException("En feil oppstod under kall til bestemSak i PESYS ex: ", ex)
            } catch (ex: Exception) {
                throw RuntimeException("En feil oppstod under kall til bestemSak i PESYS ex: ", ex)
            }
        }
    }
}

data class BestemSakRequest(val aktoerId: String,
                            val ytelseType: SakType,
                            val callId: UUID,
                            val consumerId: UUID)

class BestemSakResponse(val feil: BestemSakFeil? = null,
                        val sakInformasjonListe: List<SakInformasjon>)

class BestemSakFeil(val feilKode: String, val feilmelding: String)

