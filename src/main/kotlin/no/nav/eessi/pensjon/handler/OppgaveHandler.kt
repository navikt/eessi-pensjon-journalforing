package no.nav.eessi.pensjon.handler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class OppgaveHandler(private val aivenOppgaveKafkaTemplate: KafkaTemplate<String, String>,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()) ) {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserOppgavemelding: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        publiserOppgavemelding = metricsHelper.init("publiserOppgavemelding")
    }

    fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserOppgavemelding.measure {
            logger.info("Opprette oppgave melding på kafka: ${aivenOppgaveKafkaTemplate.defaultTopic}  melding: $melding")
            aivenOppgaveKafkaTemplate.sendDefault(key, payload).get()
        }
    }
}
