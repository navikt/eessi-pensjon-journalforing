package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*

class Pbuc10 : OppgaveRouting, RoutingHelper() {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        return if (routingRequest.bosatt == NORGE) {
            if (routingRequest.ytelseType == YtelseType.UFOREP) UFORE_UTLANDSTILSNITT
            else NFP_UTLAND_AALESUND
        } else {
            if (routingRequest.ytelseType == YtelseType.UFOREP) UFORE_UTLAND
            else PENSJON_UTLAND
        }
    }
}