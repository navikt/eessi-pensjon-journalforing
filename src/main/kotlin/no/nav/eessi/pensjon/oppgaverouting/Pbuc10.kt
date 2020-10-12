package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.YtelseType

class Pbuc10 : BucTilEnhetHandler {
    override fun hentEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        return if (routingRequest.bosatt == Bosatt.NORGE) {
            if (routingRequest.ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (routingRequest.ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}
