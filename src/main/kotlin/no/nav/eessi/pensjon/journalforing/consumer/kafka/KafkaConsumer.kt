package no.nav.eessi.pensjon.journalforing.consumer.kafka

import org.springframework.messaging.MessageHeaders

import no.nav.eessi.pensjon.journalforing.integration.model.SedHendelse
import org.springframework.kafka.support.Acknowledgment

interface KafkaConsumer {
    fun consume(hendelse: String, acknowledgment : Acknowledgment)
}