package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType

class Rbuc02 : BucTilEnhetHandler {
    override fun hentEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        val ytelseType = routingRequest.ytelseType

        if (routingRequest.identifisertPerson != null && routingRequest.identifisertPerson.flereEnnEnPerson()) {
            return Enhet.ID_OG_FORDELING
        }

        if (routingRequest.sedType == SedType.R004) {
            return Enhet.OKONOMI_PENSJON
        }

        if (HendelseType.MOTTATT == routingRequest.hendelseType) {
            return when (ytelseType) {
                YtelseType.ALDER -> Enhet.PENSJON_UTLAND
                YtelseType.UFOREP -> Enhet.UFORE_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }
        }
        return Enhet.ID_OG_FORDELING
    }
}