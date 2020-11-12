package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode.SPFO
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode.SPSF

class Pbuc05 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        val ageIsBetween18and60 = request.fdato.ageIsBetween18and60()

        // TODO: Gjøre sjekk på om "2 personer" og BARN
        if (request.diskresjonskode != null) {
            return when (request.diskresjonskode) {
                SPFO -> Enhet.ID_OG_FORDELING
                SPSF -> Enhet.DISKRESJONSKODE
                else -> Enhet.AUTOMATISK_JOURNALFORING
            }
        }

        return if (request.bosatt == Bosatt.NORGE) {
            if (ageIsBetween18and60) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (ageIsBetween18and60) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}