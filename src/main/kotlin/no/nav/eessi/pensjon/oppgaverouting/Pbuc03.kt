package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*

class Pbuc03 : OppgaveRouting {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if(routingRequest.bosatt == NORGE) {
            UFORE_UTLANDSTILSNITT
        }
        else {
            UFORE_UTLAND
        }
    }
}