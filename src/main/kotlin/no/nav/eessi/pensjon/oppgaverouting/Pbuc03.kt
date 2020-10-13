package no.nav.eessi.pensjon.oppgaverouting

class Pbuc03 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return if(request.bosatt == Bosatt.NORGE) Enhet.UFORE_UTLANDSTILSNITT
        else Enhet.UFORE_UTLAND
    }
}