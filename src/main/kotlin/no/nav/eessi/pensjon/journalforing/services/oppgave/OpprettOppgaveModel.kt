package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel

data class OpprettOppgaveModel(
        var sedHendelse: SedHendelseModel,
        var journalPostResponse: JournalPostResponse?,
        var aktoerId: String?,
        var landkode: String?,
        var fodselsDato: String,
        var ytelseType: OppgaveRoutingModel.YtelseType?,
        var oppgaveType: Oppgave.OppgaveType,
        var rinaSakId: String?,
        var filnavn: List<String>?)

