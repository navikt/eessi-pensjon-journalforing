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
class KafkaStoppingErrorHandler : CommonContainerStoppingErrorHandler() {
    private val logger = LoggerFactory.getLogger(KafkaStoppingErrorHandler::class.java)

    override fun handleRemaining(
        thrownException: java.lang.Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer
    ) {
        logger.error("En feil oppstod under kafka konsumering av meldinger: \n" + textListingOf(records) +
                "\nStopper containeren ! Restart er nødvendig for å fortsette konsumering", thrownException)
        super.handleRemaining(thrownException, records, consumer, container)
    }

    fun textListingOf(records: List<ConsumerRecord<*, *>>) =
        records.joinToString(separator = "\n") {
            "-" .repeat(20) + "\n" + vaskFnr(it.toString())
        }

    private fun vaskFnr(tekst: String) = tekst.replace(Regex("""\b\d{11}\b"""), "***")
}
