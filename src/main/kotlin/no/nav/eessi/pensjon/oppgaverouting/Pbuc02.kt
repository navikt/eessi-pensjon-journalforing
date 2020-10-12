package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*

class Pbuc02 : OppgaveRouting {
    override fun route(routingRequest: OppgaveRoutingRequest): Enhet =
            when (routingRequest.bosatt) {
                NORGE -> {
                    when (routingRequest.ytelseType) {
                        YtelseType.UFOREP -> if (routingRequest.sakStatus != SakStatus.AVSLUTTET) UFORE_UTLANDSTILSNITT else ID_OG_FORDELING
                        YtelseType.ALDER -> NFP_UTLAND_AALESUND
                        YtelseType.BARNEP -> NFP_UTLAND_AALESUND
                        YtelseType.GJENLEV -> NFP_UTLAND_AALESUND
                        else -> ID_OG_FORDELING
                    }
                }
                else -> {
                    when (routingRequest.ytelseType) {
                        YtelseType.UFOREP -> if (routingRequest.sakStatus != SakStatus.AVSLUTTET) UFORE_UTLAND else ID_OG_FORDELING
                        YtelseType.ALDER -> PENSJON_UTLAND
                        YtelseType.BARNEP -> PENSJON_UTLAND
                        YtelseType.GJENLEV -> PENSJON_UTLAND
                        else -> ID_OG_FORDELING
                    }
                }
            }
}
