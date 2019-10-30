package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.listener.*
import org.springframework.stereotype.Component
import java.lang.Exception

@Profile("prod")
@Component
class KafkaErrorHandler : ContainerAwareErrorHandler {
    private val logger = LoggerFactory.getLogger(KafkaErrorHandler::class.java)

    private val stopper = ContainerStoppingErrorHandler()

    override fun handle(thrownException: Exception?, records: MutableList<ConsumerRecord<*, *>>?, consumer: Consumer<*, *>?, container: MessageListenerContainer?) {
        logger.error("En feil oppstod under kafka konsumering. Stopper containeren ! Restart er nødvendig for å fortsette konsumering")
        stopper.handle(thrownException, records, consumer, container)
    }
 }
