package no.nav.eessi.pensjon.automatisering

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import java.time.LocalDateTime

data class AutomatiseringMelding(
    val bucId: String,
    val sedId: String,
    val sedVersjon: String,
    val opprettetTidspunkt: LocalDateTime,
    val bleAutomatisert: Boolean,
    val oppgaveEierEnhet: String?,
    val bucType: BucType,
    val sedType: SedType,
    val sakType: Saktype?,
    val hendelsesType: HendelseType
)


class KafkaAutomatiseringMessage(
    private val payload: AutomatiseringMelding
): Message<AutomatiseringMelding> {
    override fun getPayload(): AutomatiseringMelding = payload
    override fun getHeaders(): MessageHeaders = MessageHeaders(mapOf("hendelsetype" to "JOURNALFORING", "opprettetTidspunkt" to LocalDateTime.now()))
}


