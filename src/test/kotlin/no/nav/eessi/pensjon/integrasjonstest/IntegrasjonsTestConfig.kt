package no.nav.eessi.pensjon.integrasjonstest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.web.client.RestTemplate
import java.time.Duration

@TestConfiguration
class IntegrasjonsTestConfig {
    @Value("\${" + EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS + "}")
    private lateinit var brokerAddresses: String
    @Value("\${KAFKA_OPPGAVE_TOPIC}") private lateinit var oppgaveTopic: String
    @Value("\${KAFKA_AUTOMATISERING_TOPIC}") private lateinit var automatiseringTopic: String

    @MockkBean
    lateinit var ClientConfigurationProperties: ClientConfigurationProperties

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
        configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configs[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configs[ConsumerConfig.GROUP_ID_CONFIG] = "eessi-pensjon-group-test"

        return DefaultKafkaConsumerFactory(configs)
    }

    @Bean
    fun kravInitialiseringKafkaTemplate(): KafkaTemplate<String, String>  = KafkaTemplate(producerFactory())

    @Bean
    fun oppgaveKafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory()).apply {
            defaultTopic = oppgaveTopic
        }
    }

    fun producerFactory(): ProducerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = org.springframework.kafka.support.serializer.JsonSerializer::class.java
        return DefaultKafkaProducerFactory(configs)
    }

    @Bean
    fun automatiseringKafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory()).apply {
            defaultTopic = automatiseringTopic
        }
    }

    @Bean
    fun bestemSakOidcRestTemplate(): RestTemplate = mockk()

    @Bean
    fun fagmodulOidcRestTemplate(): RestTemplate  = mockk()

    @Bean
    fun proxyOAuthRestTemplate(): RestTemplate = mockk()

    @Bean
    fun journalpostOidcRestTemplate(): RestTemplate = mockedRestTemplate()

    @Bean
    fun euxOAuthRestTemplate(): RestTemplate  = mockedRestTemplate()

    @Bean
    fun euxKlient(): EuxKlientLib = EuxKlientLib(euxOAuthRestTemplate())

    @Bean
    fun pdlRestTemplate(): RestTemplate = mockedRestTemplate()

    private fun mockedRestTemplate(): RestTemplate {
        val port = System.getProperty("mockServerport")
        return RestTemplateBuilder()
            .rootUri("http://localhost:${port}")
            .build()
    }

    fun aivenKafkaConsumerFactory(): ConsumerFactory<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        configMap[ConsumerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-journalforing"
        configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = brokerAddresses
        configMap[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configMap[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configMap[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1

        return DefaultKafkaConsumerFactory(configMap, StringDeserializer(), StringDeserializer())
    }

    @Bean("sedKafkaListenerContainerFactory")
    fun aivenSedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = aivenKafkaConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.setAuthExceptionRetryInterval( Duration.ofSeconds(4L) )
        return factory
    }
}