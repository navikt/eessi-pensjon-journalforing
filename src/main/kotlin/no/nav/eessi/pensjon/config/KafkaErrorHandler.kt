package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.listener.ContainerAwareErrorHandler
import org.springframework.kafka.listener.ContainerStoppingErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.io.StringWriter


@Profile("prod")
@Component
class KafkaErrorHandler : ContainerAwareErrorHandler {
    private val logger = LoggerFactory.getLogger(KafkaErrorHandler::class.java)

    private val stopper = ContainerStoppingErrorHandler()

    override fun handle(
        thrownException: java.lang.Exception,
        records: MutableList<ConsumerRecord<*, *>>?,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer
    ) {
        val stacktrace = StringWriter()
        thrownException.printStackTrace(PrintWriter(stacktrace))

        logger.error("En feil oppstod under kafka konsumering av meldinger: \n ${hentMeldinger(records)} \n" +
                "Stopper containeren ! Restart er nødvendig for å fortsette konsumering, $stacktrace")
        stopper.handle(thrownException, records, consumer, container)
    }

    fun hentMeldinger(records: MutableList<ConsumerRecord<*, *>>?): String {
        var meldinger = ""
        records?.forEach { it ->
            meldinger += "--------------------------------------------------------------------------------\n"
            meldinger += it.toString()
            meldinger += "\n"
        }
        return meldinger
    }

}
