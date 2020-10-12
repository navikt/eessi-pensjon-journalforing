package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*

class Pbuc08 : OppgaveRouting, RoutingHelper() {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if (routingRequest.bosatt == NORGE) {
            if (isBetween18and60(routingRequest.fdato))
                UFORE_UTLANDSTILSNITT
            else NFP_UTLAND_AALESUND
        } else {
            if (isBetween18and60(routingRequest.fdato)) UFORE_UTLAND
            else PENSJON_UTLAND
        }
    }
}