package no.nav.eessi.pensjon.oppgaverouting

class Pbuc03 : BucTilEnhetHandler {
    override fun hentEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        return if(routingRequest.bosatt == Bosatt.NORGE) Enhet.UFORE_UTLANDSTILSNITT
        else Enhet.UFORE_UTLAND
    }
}