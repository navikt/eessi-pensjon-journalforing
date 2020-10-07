package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest

class Rbuc02 : Buc, RoutingHelper() {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        val ytelseType = routingRequest.ytelseType

        if (routingRequest.identifisertPerson != null && routingRequest.identifisertPerson.flereEnnEnPerson()) {
            return ID_OG_FORDELING
        }

        if (routingRequest.sedType == SedType.R004) {
            return OKONOMI_PENSJON
        }

        if (HendelseType.MOTTATT == routingRequest.hendelseType) {
            return when (ytelseType) {
                YtelseType.ALDER -> PENSJON_UTLAND
                YtelseType.UFOREP -> UFORE_UTLAND
                else -> ID_OG_FORDELING
            }
        }
        return ID_OG_FORDELING
    }
}