package no.nav.eessi.pensjon.handeler

import no.nav.eessi.pensjon.json.toJson

data class OppgaveMelding(
        val sedType : String?,
        val journalpostId : String?,
        val tildeltEnhetsnr : String,
        val aktoerId : String?,
        val oppgaveType : String,
        val rinaSakId : String,
        val hendelseType : String?,
        var filnavn : String?
    )  {
    override fun toString(): String {
        return toJson()
    }
}