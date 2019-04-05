package no.nav.eessi.pensjon.journalforing

data class KafkaConfig(
    val brokers: String,
    val username: String,
    val password: String,
    val schemaRegistryUrl: String,
    val securityProtocol: String = "SASL_SSL")
