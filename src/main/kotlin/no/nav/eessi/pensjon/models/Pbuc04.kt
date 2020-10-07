package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest

class Pbuc04 : Buc {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if(routingRequest.bosatt == NORGE) {
            NFP_UTLAND_AALESUND
        }
        else {
            PENSJON_UTLAND
        }
    }
}