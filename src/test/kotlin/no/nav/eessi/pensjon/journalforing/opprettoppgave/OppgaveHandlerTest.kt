package no.nav.eessi.pensjon.journalforing.opprettoppgave

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType.*
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

private val GJENNY_SED = P2100
private const val JPID = "123456"
private const val AKTOERID = "12345678932"
private const val RINASAK = "654321"

class OppgaveHandlerTest {

    private var oppgaveHandler: OppgaveHandler = mockk()
    private var etterlatteService: EtterlatteService = mockk()
    private var oppgaveKafkaTemplate: KafkaTemplate<String, String> = mockk()

    @BeforeEach
    fun setUp() {
        oppgaveHandler = OppgaveHandler(oppgaveKafkaTemplate, etterlatteService)
        every { etterlatteService.opprettGjennyOppgave(any()) } returns Result.success(JPID)

        val future: CompletableFuture<SendResult<String, String>> = CompletableFuture()
        future.complete(SendResult(ProducerRecord("gjenny", "gjenny"), mockk()) )

        every { oppgaveKafkaTemplate.sendDefault(any(), any()) } returns future
        every { oppgaveKafkaTemplate.defaultTopic } returns "oppgave"

    }

    @Test
    fun `Ved journalforing av innkommende gjenny sed så skal det ikke opprettes gjenny-journalforingsoppgave`() {

        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(
            OppgaveMelding(
                sedType = GJENNY_SED,
                journalpostId = JPID,
                tildeltEnhetsnr = NFP_UTLAND_AALESUND,
                aktoerId = AKTOERID,
                rinaSakId = RINASAK,
                hendelseType = MOTTATT,
                filnavn = "",
                oppgaveType = JOURNALFORING,
                tema = Tema.OMSTILLING
            )
        )

        verify(exactly = 0) { etterlatteService.opprettGjennyOppgave(any()) }
        verify(exactly = 1) { oppgaveKafkaTemplate.sendDefault(any(), any()) }
    }

    @Test
    fun `Ved journalforing av utgående gjenny sed så skal det opprettes gjenny-journalforingsoppgave`() {

        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(
            OppgaveMelding(
                GJENNY_SED,
                JPID,
                NFP_UTLAND_AALESUND,
                AKTOERID,
                JPID,
                SENDT,
                 "",
                JOURNALFORING_UT,
                Tema.OMSTILLING
            )
        )

        verify(exactly = 1) { etterlatteService.opprettGjennyOppgave(any()) }
        verify(exactly = 0) { oppgaveKafkaTemplate.sendDefault(any(), any()) }
    }
    

}