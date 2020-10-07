package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest

class Pbuc06 : Buc, RoutingHelper() {
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