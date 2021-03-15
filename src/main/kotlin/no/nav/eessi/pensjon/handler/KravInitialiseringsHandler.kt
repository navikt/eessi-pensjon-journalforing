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
class KravInitialiseringsHandler(private val kafkaTemplate: KafkaTemplate<String, String>,
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
        kafkaTemplate.defaultTopic = kravTopic

        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserKravmelding.measure {
            logger.info("Opprette melding på kafka: ${kafkaTemplate.defaultTopic}  melding: $payload")
            kafkaTemplate.sendDefault(key, payload).get()
        }
    }

}
