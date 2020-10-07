package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest

class Pbuc10 : Buc, RoutingHelper() {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if (routingRequest.ytelseType == YtelseType.UFOREP) UFORE_UTLAND
        else PENSJON_UTLAND
    }
}