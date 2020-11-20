package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

class DefaultBucTilEnhetHandler : BucTilEnhetHandler() {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        val ageIsBetween18and60 = request.fdato.ageIsBetween18and60()

        return if (request.bosatt == Bosatt.NORGE) {
            if (ageIsBetween18and60) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (ageIsBetween18and60) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}
