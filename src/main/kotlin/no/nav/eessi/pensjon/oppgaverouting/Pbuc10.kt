package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.YtelseType

class Pbuc10 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return if (request.bosatt == Bosatt.NORGE) {
            if (request.ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (request.ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}
