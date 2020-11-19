package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

class Pbuc03 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.ytelseType != null -> Enhet.AUTOMATISK_JOURNALFORING
            request.bosatt == Bosatt.NORGE -> Enhet.UFORE_UTLANDSTILSNITT
            else -> Enhet.UFORE_UTLAND
        }
    }
}
