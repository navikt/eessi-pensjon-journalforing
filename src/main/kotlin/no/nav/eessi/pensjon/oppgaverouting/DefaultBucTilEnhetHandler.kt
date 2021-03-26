package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

class DefaultBucTilEnhetHandler : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return if (request.harAdressebeskyttelse)
            Enhet.DISKRESJONSKODE
        else
            enhetFraAlderOgLand(request)
    }

    private fun enhetFraAlderOgLand(request: OppgaveRoutingRequest): Enhet {
        val ageIsBetween18and62 = request.fdato.ageIsBetween18and62()

        return if (request.bosatt == Bosatt.NORGE) {
            if (ageIsBetween18and62) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (ageIsBetween18and62) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}
