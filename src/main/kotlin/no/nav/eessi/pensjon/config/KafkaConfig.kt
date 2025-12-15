package no.nav.eessi.pensjon.config

import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration

@EnableKafka
@Profile("prod", "test")
@Configuration
class KafkaConfig(
    @param:Value("\${kafka.keystore.path}") private val keystorePath: String,
    @param:Value("\${kafka.credstore.password}") private val credstorePassword: String,
    @param:Value("\${kafka.truststore.path}") private val truststorePath: String,
    @param:Value("\${kafka.brokers}") private val bootstrapServers: String,
    @param:Value("\${kafka.security.protocol}") private val securityProtocol: String,
    @Autowired private val kafkaErrorHandler: KafkaStoppingErrorHandler?,
    @Value("\${KAFKA_AUTOMATISERING_TOPIC}") private val automatiseringTopic: String,
    @Value("\${KAFKA_OPPGAVE_TOPIC}") private val oppgaveTopic: String) {

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        populerCommonConfig(configMap)
        configMap[ProducerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-journalforing"
        configMap[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        return DefaultKafkaProducerFactory(configMap)
    }

    @Bean
    fun kravInitialiseringKafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun statistikkKafkaTemplate(): KafkaTemplate<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        populerCommonConfig(configMap)
        configMap[ProducerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-journalforing"
        configMap[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonJsonSerializer::class.java
        configMap[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        val automatiseringsTemplate: ProducerFactory<String, String> = DefaultKafkaProducerFactory(configMap)

        val template = KafkaTemplate(automatiseringsTemplate)
        template.setDefaultTopic(automatiseringTopic)
        return template
    }

    @Bean
    fun oppgaveKafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory()).apply {
            setDefaultTopic(oppgaveTopic)
        }
    }

    fun kafkaConsumerFactory(): ConsumerFactory<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        populerCommonConfig(configMap)
        configMap[ConsumerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-journalforing"
        configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configMap[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configMap[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configMap[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1

        return DefaultKafkaConsumerFactory(configMap, StringDeserializer(), StringDeserializer())
    }

    @Bean
    fun sedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(kafkaConsumerFactory())
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.setAuthExceptionRetryInterval( Duration.ofSeconds(4L) )
        if (kafkaErrorHandler != null) {
            factory.setCommonErrorHandler(kafkaErrorHandler)
        }
        return factory
    }


    private fun populerCommonConfig(configMap: MutableMap<String, Any>) {
        configMap[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystorePath
        configMap[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = credstorePassword
        configMap[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = credstorePassword
        configMap[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = credstorePassword
        configMap[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
        configMap[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
        configMap[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststorePath
        configMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = securityProtocol
    }

}