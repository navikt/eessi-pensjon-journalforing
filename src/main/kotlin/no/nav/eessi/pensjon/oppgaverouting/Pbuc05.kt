package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode

class Pbuc05 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            flerePersoner(request) -> hentEnhetForRelasjon(request)
            journalforesAutomatisk(request) -> Enhet.AUTOMATISK_JOURNALFORING
            request.sakInformasjon == null -> Enhet.ID_OG_FORDELING
            else -> enhetFraAlderOgLand(request)
        }
    }

    /**
     * Sjekker om saken inneholder mer enn 1 person.
     */
    private fun flerePersoner(request: OppgaveRoutingRequest): Boolean {
        val personer = request.identifisertPerson?.personListe ?: emptyList()

        return personer.size > 1
    }

    /**
     * Henter ut enhet basert på [Diskresjonskode].
     * Skal kun brukes dersom det finnes en [IdentifisertPerson] med [Relasjon.BARN]
     */
    private fun hentEnhetForRelasjon(request: OppgaveRoutingRequest): Enhet {
        val personer = request.identifisertPerson?.personListe ?: emptyList()

        return when {
            personer.any { it.personRelasjon.relasjon == Relasjon.BARN } -> enhetForRelasjonBarn(request)
            personer.any { it.personRelasjon.relasjon == Relasjon.FORSORGER } -> enhetForRelasjonForsorger(request)
            else -> enhetFraAlderOgLand(request)
        }
    }

    private fun enhetForRelasjonBarn(request: OppgaveRoutingRequest): Enhet {
        val ytelseType = request.sakInformasjon?.sakType ?: request.ytelseType

        // I tilfeller hvor sakInformasjon er null
        return when (ytelseType) {
            YtelseType.ALDER,
            YtelseType.UFOREP,
            YtelseType.OMSORG -> Enhet.AUTOMATISK_JOURNALFORING
            else -> if (request.bosatt == Bosatt.NORGE) Enhet.NFP_UTLAND_AALESUND else Enhet.PENSJON_UTLAND
        }
    }

    private fun enhetForRelasjonForsorger(request: OppgaveRoutingRequest): Enhet {
        return when (request.ytelseType) {
            null, YtelseType.GENRL -> enhetFraAlderOgLand(request)
            else -> Enhet.AUTOMATISK_JOURNALFORING
        }
    }

    /**
     * Sjekker om [YtelseType] er av en type som er godkjent for [Enhet.AUTOMATISK_JOURNALFORING]
     */
    private fun journalforesAutomatisk(request: OppgaveRoutingRequest): Boolean {
        // TODO: Ingen sak skal gi [Enhet.ID_OG_FORDELING]
        val sakInfo = request.sakInformasjon
        return (kanAutomatiskJournalfores(request) && sakInfo != null && sakInfo.harGenerellSakTypeMedTilknyttetSaker().not())
    }

    /**
     * Henter ut [Enhet] basert på den forsikrede sin [Bosatt] og alder.
     */
    private fun enhetFraAlderOgLand(request: OppgaveRoutingRequest): Enhet {
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
