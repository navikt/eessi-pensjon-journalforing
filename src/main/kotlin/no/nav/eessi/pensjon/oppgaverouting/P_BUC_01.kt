package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*

class P_BUC_01 : OppgaveRouting {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if(routingRequest.bosatt == NORGE) {
            NFP_UTLAND_AALESUND
        }
        else {
            PENSJON_UTLAND
        }
    }
}