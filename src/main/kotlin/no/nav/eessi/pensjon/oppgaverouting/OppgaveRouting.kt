package no.nav.eessi.pensjon.oppgaverouting

interface OppgaveRouting {
    fun route(routingRequest: OppgaveRoutingRequest) : OppgaveRoutingModel.Enhet
}