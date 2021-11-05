package no.nav.eessi.pensjon.handler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class KravInitialiseringsHandler(private val aivenKravInitialiseringKafkaTemplate: KafkaTemplate<String, String>,
                                 @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()) ) {

    private val logger = LoggerFactory.getLogger(KravInitialiseringsHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserKravmelding: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        publiserKravmelding = metricsHelper.init("publiserKravmelding")
    }

    @Value("\${kafka.krav.topic}")
    private lateinit var kravTopic: String

    fun putKravInitMeldingPaaKafka(melding: BehandleHendelseModel) {
        aivenKravInitialiseringKafkaTemplate.defaultTopic = kravTopic

        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserKravmelding.measure {
            logger.info("Oppretter krav initialisering melding på kafka: ${aivenKravInitialiseringKafkaTemplate.defaultTopic}  melding: $payload")
           try {
               aivenKravInitialiseringKafkaTemplate.sendDefault(key, payload).get()
           } catch (ex: Exception) {
               logger.error("Noe gikk galt under opprettelse av krav initialisering melding: $ex")
               throw ex
           }
        }
    }

}
