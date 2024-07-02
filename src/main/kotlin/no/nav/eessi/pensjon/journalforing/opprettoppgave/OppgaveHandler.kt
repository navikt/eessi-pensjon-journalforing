package no.nav.eessi.pensjon.journalforing.opprettoppgave

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class OppgaveHandler(private val oppgaveKafkaTemplate: KafkaTemplate<String, String>,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest() ) {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserOppgavemelding: MetricsHelper.Metric

    init {
        publiserOppgavemelding = metricsHelper.init("publiserOppgavemelding")
    }

    fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserOppgavemelding.measure {
            logger.info("Opprette ${melding.oppgaveType}-oppgave melding på kafka: ${oppgaveKafkaTemplate.defaultTopic}  melding: $melding")
            oppgaveKafkaTemplate.sendDefault(key, payload).get()
        }
    }

    fun oppdaterOppgaveMeldingPaaKafkaTopic(melding: OppdaterOppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserOppgavemelding.measure {
            logger.info("Oppdaterer ${melding.id}-oppgave melding på kafka: ${oppgaveKafkaTemplate.defaultTopic}  melding: $melding")
            oppgaveKafkaTemplate.sendDefault(key, payload).get()
        }
    }
}
