package no.nav.eessi.pensjon.automatisering

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import java.time.LocalDateTime

data class StatistikkMelding(
    val bucId: String,
    val sedId: String,
    val sedVersjon: String,
    val opprettetTidspunkt: LocalDateTime,
    val oppgaveEierEnhet: String?,
    val bucType: BucType,
    val sedType: SedType,
    val sakType: SakType?,
    val hendelsesType: HendelseType
)


class KafkaStatistikkMessage(private val payload: StatistikkMelding): Message<StatistikkMelding> {
    override fun getPayload(): StatistikkMelding = payload
    override fun getHeaders(): MessageHeaders = MessageHeaders(mapOf("hendelsetype" to "JOURNALFORING", "opprettetTidspunkt" to LocalDateTime.now()))
}


