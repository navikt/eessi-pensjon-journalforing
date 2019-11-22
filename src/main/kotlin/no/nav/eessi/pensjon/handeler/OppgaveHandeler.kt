package no.nav.eessi.pensjon.handeler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service

@Service
class OppgaveHandeler(val kafkaTemplate: KafkaTemplate<String, String>,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()))  {

    val X_REQUEST_ID = "x_request_id"

    @Value("\${oppgaveTopic}")
    lateinit var oppgaveTopic: String

    @Value("\${FASIT_ENVIRONMENT_NAME}")
    lateinit var topicPostfix: String


    fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val topic = "$oppgaveTopic-$topicPostfix"
        kafkaTemplate.defaultTopic = topic

        val messageBuilder = MessageBuilder.withPayload(melding)

        metricsHelper.measure("selvbetjeningsinfoprodusert") {
            if (MDC.get(X_REQUEST_ID).isNullOrEmpty()) {
                val message = messageBuilder.build()
                kafkaTemplate.send(message)
            } else {
                val message = messageBuilder.setHeader(X_REQUEST_ID, MDC.get(X_REQUEST_ID)).build()
                kafkaTemplate.send(message)
            }
        }
    }

}