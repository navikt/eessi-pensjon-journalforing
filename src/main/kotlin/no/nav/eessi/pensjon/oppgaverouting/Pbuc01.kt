package no.nav.eessi.pensjon.oppgaverouting

class Pbuc01 : BucTilEnhetHandler {
    override fun hentEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        return if(routingRequest.bosatt == Bosatt.NORGE) Enhet.NFP_UTLAND_AALESUND
        else Enhet.PENSJON_UTLAND
    }
}