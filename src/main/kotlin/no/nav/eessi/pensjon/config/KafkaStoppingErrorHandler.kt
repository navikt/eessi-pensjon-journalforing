package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.io.StringWriter

@Profile("prod")
@Component
class KafkaStoppingErrorHandler : CommonErrorHandler {
    private val logger = LoggerFactory.getLogger(KafkaStoppingErrorHandler::class.java)
    private val stopper = CommonContainerStoppingErrorHandler()

    override fun handleRecord(
        thrownException: Exception,
        record: ConsumerRecord<*, *>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer) {

        val stacktrace = StringWriter()
        thrownException.printStackTrace(PrintWriter(stacktrace))

        logger.error("En feil oppstod under kafka konsumering av meldinger: \n" + textListingOf(record ) +
                "\nStopper containeren ! Restart er nødvendig for å fortsette konsumering, $stacktrace")
        stopper.handleRemaining(thrownException, listOf(record), consumer, container)
    }
    fun textListingOf(records: ConsumerRecord<*, *>) = "-" .repeat(20) + records.toString()

}
