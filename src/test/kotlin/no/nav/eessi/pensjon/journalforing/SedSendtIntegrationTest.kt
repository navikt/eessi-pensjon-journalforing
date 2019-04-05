package no.nav.eessi.pensjon.journalforing

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

@ActiveProfiles("integrationtest")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integrationtest")
@ContextConfiguration(classes = [
    TestKafkaEnvironmentConfig::class
])
internal class SedSendtIntegrationTest {

    @LocalServerPort
    var localServerPort: Int = 0

    @Autowired
    lateinit var testKafkaProducer: KafkaProducer<String, String>

    @Test
    fun contextLoads() {
        assertNotEquals(0, localServerPort)
    }

    @Test
    fun mottak_av_selvbetjeningsinfo() {

        // GITT


        // NÅR


        // SÅ

    }
}

@Profile("integrationtest")
@TestConfiguration
class TestKafkaEnvironmentConfig {

    val testProducerUsername = "yoyo_testusername"
    val testProducerPassword = "yoyo_testpassword"
    val testSecurityProtocol = "SASL_PLAINTEXT"

    @Value("\${srveessipensjon.username}")
    lateinit var appConsumerUsername: String

    @Value("\${srveessipensjon.password}")
    lateinit var appConsumerPassword: String

    @Value("\${schemaregistry.url:http://kafka-test-schema-registry.tpa:8081}")
    lateinit var schemaRegistryUrl: String


    var kafkaEnvironment: KafkaEnvironment? = null

    @Bean
    fun kafkaConfigCreator(): KafkaConfig =
            KafkaConfig(
                    kafkaEnvironment().brokersURL,
                    appConsumerUsername,
                    appConsumerPassword,
                    schemaRegistryUrl,
                    testSecurityProtocol)

    @Bean
    fun kafkaEnvironmentCreator(): KafkaEnvironment = kafkaEnvironment()

    private fun kafkaEnvironment() : KafkaEnvironment {
        if (kafkaEnvironment == null) {
            kafkaEnvironment = KafkaEnvironment(
                    topicNames = listOf("noe"),
                    withSecurity = true,
                    users = listOf(
                            JAASCredential(appConsumerUsername, appConsumerPassword),
                            JAASCredential(testProducerUsername, testProducerPassword)),
                    autoStart = true)
        }

        kafkaEnvironment!!.adminClient!!.createAcls(
                    createConsumerACL(mapOf(
                            "noe" to appConsumerUsername))
                        union
                    createProducerACL(mapOf(
                            "noe" to testProducerUsername)))
        kafkaEnvironment!!.adminClient!!.close()

        return kafkaEnvironment!!
    }

    @Bean
    fun testKafkaProducer(kafkaEnvironment: KafkaEnvironment): KafkaProducer<String, String> {
        val config = mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaEnvironment.brokersURL,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.CLIENT_ID_CONFIG to "testProducer",
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.RETRIES_CONFIG to Integer.MAX_VALUE,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to testSecurityProtocol,
                SaslConfigs.SASL_MECHANISM to "PLAIN",
                SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$testProducerUsername\" password=\"$testProducerPassword\";"
        )
        return KafkaProducer(config)
    }

}