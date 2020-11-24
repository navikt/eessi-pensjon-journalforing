package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

class Pbuc01 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            erStrengtFortrolig(request.diskresjonskode) -> Enhet.DISKRESJONSKODE
            kanAutomatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            request.bosatt == Bosatt.NORGE -> Enhet.NFP_UTLAND_AALESUND
            else -> Enhet.PENSJON_UTLAND
        }
    }
}
