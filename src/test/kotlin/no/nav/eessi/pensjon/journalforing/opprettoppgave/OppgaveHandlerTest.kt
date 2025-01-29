package no.nav.eessi.pensjon.journalforing.opprettoppgave

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

class OppgaveHandlerTest {

    private var oppgaveHandler: OppgaveHandler = mockk()
    private var etterlatteService: EtterlatteService = mockk()
    private var oppgaveKafkaTemplate: KafkaTemplate<String, String> = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        oppgaveHandler = OppgaveHandler(oppgaveKafkaTemplate, etterlatteService)
        every { etterlatteService.opprettGjennyOppgave(any()) } returns Result.success("123456")

        val future: CompletableFuture<SendResult<String, String>> = CompletableFuture()
        every { oppgaveKafkaTemplate.sendDefault(any(), any()) } returns future
    }

    @Test
    fun `kdufkhj`() {

        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(
            OppgaveMelding(
                sedType = SedType.P2100,
                journalpostId = "123456",
                tildeltEnhetsnr = Enhet.NFP_UTLAND_AALESUND,
                aktoerId = "12345678932",
                rinaSakId = "123456",
                hendelseType = HendelseType.MOTTATT,
                filnavn = "",
                oppgaveType = OppgaveType.JOURNALFORING,
                tema = Tema.OMSTILLING
            )
        )

        verify(exactly = 0) { etterlatteService.opprettGjennyOppgave(any()) }

    }


}