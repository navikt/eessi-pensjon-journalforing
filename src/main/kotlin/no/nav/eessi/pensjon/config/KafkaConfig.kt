package no.nav.eessi.pensjon.config

import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile

@Profile("prod")
@Configuration
class KafkaConfig()  {

    /**
     * Denne konfigurasjonen forsøker å behandle en melding en gang og stopper videre behandling av nye meldinger dersom det feiler
     */
    @Bean
    fun kafkaListenerContainerFactory(configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
                                      kafkaConsumerFactory: ConsumerFactory<Any, Any>,
                                      kafkaErrorHandler: KafkaErrorHandler): ConcurrentKafkaListenerContainerFactory<*, *> {
        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        configurer.configure(factory, kafkaConsumerFactory)
        factory.setErrorHandler(kafkaErrorHandler)
        return factory
    }
}