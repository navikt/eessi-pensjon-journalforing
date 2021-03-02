package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson

/**
 * R_BUC_02: Motregning av overskytende utbetaling i etterbetalinger
 */
class Rbuc02 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.harAdressebeskyttelse -> Enhet.DISKRESJONSKODE
            erPersonUgyldig(request.identifisertPerson) -> Enhet.ID_OG_FORDELING
            request.sedType == SedType.R004 -> Enhet.OKONOMI_PENSJON
            kanAutomatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            else -> hentEnhetForYtelse(request)
        }
    }

    private fun hentEnhetForYtelse(request: OppgaveRoutingRequest): Enhet {
        if (request.hendelseType == HendelseType.SENDT) return Enhet.ID_OG_FORDELING

        return when (request.saktype) {
            Saktype.ALDER -> Enhet.PENSJON_UTLAND
            Saktype.UFOREP -> Enhet.UFORE_UTLAND
            else -> Enhet.ID_OG_FORDELING
        }
    }

    private fun erPersonUgyldig(person: IdentifisertPerson?): Boolean =
            person == null || person.aktoerId.isBlank() || person.flereEnnEnPerson()
}
