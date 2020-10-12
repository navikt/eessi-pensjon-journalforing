package no.nav.eessi.pensjon.oppgaverouting

class DefaultBucTilEnhetHandler : BucTilEnhetHandler {
    override fun hentEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        val ageIsBetween18and60 = routingRequest.fdato.ageIsBetween18and60()

        return if (routingRequest.bosatt == Bosatt.NORGE) {
            if (ageIsBetween18and60) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (ageIsBetween18and60) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}