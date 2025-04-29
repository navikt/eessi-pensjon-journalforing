package no.nav.eessi.pensjon.journalforing.opprettoppgave

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OpprettOppgaveService(private val oppgaveHandler: OppgaveHandler) {

    private val logger = LoggerFactory.getLogger(OpprettOppgaveService::class.java)

    fun opprettBehandleSedOppgave(
        journalpostId: String? = null,
        oppgaveEnhet: Enhet,
        aktoerId: String? = null,
        sedHendelseModel: SedHendelse,
        uSupporterteVedlegg: String? = null,
        tema: Tema
    ) {
        if (sedHendelseModel.avsenderLand != "NO") {
            val oppgave = OppgaveMelding(
                sedHendelseModel.sedType,
                journalpostId,
                oppgaveEnhet,
                aktoerId,
                sedHendelseModel.rinaSakId,
                MOTTATT,
                uSupporterteVedlegg,
                OppgaveType.BEHANDLE_SED,
                tema
            )
            oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(oppgave)
        } else logger.warn("Nå har du forsøkt å opprette en BEHANDLE_SED oppgave, men avsenderland er Norge.")
    }

    fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
    }
}