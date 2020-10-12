package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*

class Rbuc02 : OppgaveRouting, RoutingHelper() {
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