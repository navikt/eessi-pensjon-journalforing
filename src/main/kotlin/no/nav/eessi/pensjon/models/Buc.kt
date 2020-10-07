package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest

interface Buc {
    fun route(routingRequest: OppgaveRoutingRequest) : Enhet
}