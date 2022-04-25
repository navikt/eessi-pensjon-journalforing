package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.ContainerAwareErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.io.StringWriter


@Profile("prod")
@Component
class KafkaStoppingErrorHandler : ContainerAwareErrorHandler {
    private val logger = LoggerFactory.getLogger(KafkaStoppingErrorHandler::class.java)

    private val stopper = CommonContainerStoppingErrorHandler()

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
        stopper.handleRemaining(thrownException, records?: emptyList(), consumer, container)
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
