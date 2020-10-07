package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest

class Pbuc03 : Buc {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if(routingRequest.bosatt == NORGE) {
            UFORE_UTLANDSTILSNITT
        }
        else {
            UFORE_UTLAND
        }
    }
}