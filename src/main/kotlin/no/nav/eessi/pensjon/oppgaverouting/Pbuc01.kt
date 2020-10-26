package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

class Pbuc01 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return if(request.bosatt == Bosatt.NORGE) Enhet.NFP_UTLAND_AALESUND
        else Enhet.PENSJON_UTLAND
    }
}
