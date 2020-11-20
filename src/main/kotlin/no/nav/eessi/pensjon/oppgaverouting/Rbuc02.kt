package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType

class Rbuc02 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        if (automatiskBehandles(request)) {
            return Enhet.AUTOMATISK_JOURNALFORING
        }
        val ytelseType = request.ytelseType

        if (request.identifisertPerson != null && request.identifisertPerson.flereEnnEnPerson()) {
            return Enhet.ID_OG_FORDELING
        }

        if (request.sedType == SedType.R004) {
            return Enhet.OKONOMI_PENSJON
        }

        if (HendelseType.MOTTATT == request.hendelseType) {
            return when (ytelseType) {
                YtelseType.ALDER -> Enhet.PENSJON_UTLAND
                YtelseType.UFOREP -> Enhet.UFORE_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }
        }
        return Enhet.ID_OG_FORDELING
    }

    private fun automatiskBehandles(request: OppgaveRoutingRequest): Boolean {
        val identifisertPerson = request.identifisertPerson
        return  (kanAutomatiskJournalfores(request)) && (request.hendelseType == HendelseType.SENDT && identifisertPerson != null && identifisertPerson.flereEnnEnPerson()).not()
    }
}
