package no.nav.eessi.pensjon.integrasjonstest

import com.fasterxml.jackson.databind.JsonSerializer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.mockk
import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.web.client.RestTemplate

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
    fun kravInitialiseringKafkaTemplate(): KafkaTemplate<String, String> {
        val template = KafkaTemplate(producerFactory())
        return template
    }

    @Bean
    fun oppgaveKafkaTemplate(): KafkaTemplate<String, String> {
        val kafka = KafkaTemplate(producerFactory())
        kafka.defaultTopic = oppgaveTopic
        return kafka
    }

    fun producerFactory(): ProducerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        return DefaultKafkaProducerFactory(configs)
    }

    @Bean
    fun automatiseringKafkaTemplate(): KafkaTemplate<String, String> {
        val kafka = KafkaTemplate(producerFactory())
        kafka.defaultTopic = automatiseringTopic
        return kafka
    }

    @Bean
    fun journalpostOidcRestTemplate(): RestTemplate{
        val port = System.getProperty("mockServerport")
        return RestTemplateBuilder()
            .rootUri("http://localhost:${port}")
            .build()
    }

    @Bean
    fun bestemSakOidcRestTemplate(): RestTemplate{
        return mockk()
    }

    @Bean
    fun euxOAuthRestTemplate(): RestTemplate {
        val port = System.getProperty("mockServerport")
        return RestTemplateBuilder()
            .rootUri("http://localhost:${port}")
            .build()
    }

    @Bean
    fun fagmodulOidcRestTemplate(): RestTemplate {
        return mockk()
    }

    @Bean
    fun proxyOAuthRestTemplate(): RestTemplate {
        return mockk()
    }

    @Bean("pdlTokenComponent")
    @Primary
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun pdlTokenComponent(): PdlTokenCallBack {
        return object : PdlTokenCallBack {
            override fun callBack(): PdlToken {
                return PdlTokenImp(accessToken = "")
            }
        }
    }
}