package no.nav.eessi.pensjon.handeler

import org.springframework.messaging.Message

data class OppgaveMelding(
        val sedType : String?,
        val journalpostId : String?,
        val tildeltEnhetsnr : String,
        val aktoerId : String?,
        val oppgaveType : String,
        val rinaSakId : String,
        val hendelseType : String?,
        var filnavn : String?
    )