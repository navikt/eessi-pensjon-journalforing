package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

/**
 * P_BUC_04: Anmodning  om opplysninger om perioder med omsorg for barn
 */
class Pbuc04 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            erStrengtFortrolig(request.diskresjonskode) -> Enhet.DISKRESJONSKODE
            kanAutomatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            request.bosatt == Bosatt.NORGE -> Enhet.NFP_UTLAND_AALESUND
            else -> Enhet.PENSJON_UTLAND
        }
    }
}
