package no.nav.eessi.pensjon.handler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service

@Service
class OppgaveHandler(private val kafkaTemplate: KafkaTemplate<String, String>,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()) ) {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    @Value("\${kafka.oppgave.topic}")
    private lateinit var oppgaveTopic: String

    @Value("\${FASIT_ENVIRONMENT_NAME}")
    private lateinit var topicPostfix: String

    private fun putMeldingPaaKafka(melding: OppgaveMelding) {
        kafkaTemplate.defaultTopic = "$oppgaveTopic-$topicPostfix"

        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        metricsHelper.measure("publiserOppgavemelding") {
            logger.info("Opprette oppgave melding på kafka: ${kafkaTemplate.defaultTopic}  melding: $melding")
            kafkaTemplate.sendDefault(key, payload)
        }
    }

    //helper function to put message on kafka, will retry 3 times and wait before fail
    fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        logger.info("Trying to publish")

        var count = 0
        val maxTries = 3
        val waitTime = 8000L
        var failException : Exception ?= null

        while (count < maxTries) {
            try {
                putMeldingPaaKafka(melding)
                logger.info("Publishing oppgavemelding")
                return
            } catch (ex: Exception) {
                count++
                logger.warn("Failed to publish oppgavemelding on kafka, try nr.: $count, Error message: ${ex.message} ")
                failException = ex
                Thread.sleep(waitTime)
            }
        }
        logger.error("Failed to publish oppgavemelding on kafka,  meesage: $melding", failException)
        throw RuntimeException(failException!!.message)

    }

}