package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode

class Pbuc05 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            erGjenlevende(request.identifisertPerson) -> hentEnhetForGjenlevende(request)
            flerePersoner(request) -> hentEnhetForRelasjon(request)
            journalforesAutomatisk(request) -> Enhet.AUTOMATISK_JOURNALFORING
            request.sakInformasjon == null -> Enhet.ID_OG_FORDELING
            else -> enhetFraAlderOgLand(request)
        }
    }

    /**
     * Sjekker om det finnes en identifisert person og om denne personen er [Relasjon.GJENLEVENDE]
     *
     * @return true dersom personen har [Relasjon.GJENLEVENDE]
     */
    private fun erGjenlevende(person: IdentifisertPerson?): Boolean =
            person?.personRelasjon?.relasjon == Relasjon.GJENLEVENDE

    /**
     * Henter enhet for [Relasjon.GJENLEVENDE]
     *
     * @return Skal returnere [Enhet.AUTOMATISK_JOURNALFORING] dersom det finnes [SakInformasjon]
     */
    private fun hentEnhetForGjenlevende(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.sakInformasjon == null -> Enhet.ID_OG_FORDELING
            request.sakInformasjon.sakType == YtelseType.GENRL -> enhetFraAlderOgLand(request)
            else -> Enhet.AUTOMATISK_JOURNALFORING
        }
    }

    /**
     * Sjekker om saken inneholder flere identifiserte personer.
     *
     * @return true dersom det finnes mer enn én person.
     */
    private fun flerePersoner(request: OppgaveRoutingRequest): Boolean {
        val personer = request.identifisertPerson?.personListe ?: emptyList()

        return personer.size > 1
    }

    /**
     * Henter ut enhet basert på [Diskresjonskode].
     * Skal kun brukes dersom det finnes en [IdentifisertPerson] med [Relasjon.BARN]
     *
     * @return Kaller ruting-funksjon basert på [Relasjon]
     */
    private fun hentEnhetForRelasjon(request: OppgaveRoutingRequest): Enhet {
        val personer = request.identifisertPerson?.personListe ?: return enhetFraAlderOgLand(request)

        return when {
            personer.any { it.personRelasjon.relasjon == Relasjon.BARN } -> enhetForRelasjonBarn(request)
            personer.any { it.personRelasjon.relasjon == Relasjon.FORSORGER } -> enhetForRelasjonForsorger(request)
            else -> enhetFraAlderOgLand(request)
        }
    }

    /**
     * Henter korrekt enhet for [Relasjon.BARN]
     *
     * @return Skal returnere [Enhet.AUTOMATISK_JOURNALFORING] dersom ytelseType er [YtelseType.ALDER],
     *  [YtelseType.UFOREP], eller [YtelseType.OMSORG]. Hvis ikke skal forenklet rutingregel følges.
     */
    private fun enhetForRelasjonBarn(request: OppgaveRoutingRequest): Enhet {
        if (request.sakInformasjon?.sakId == null) return Enhet.ID_OG_FORDELING

        // I tilfeller hvor sakInformasjon er null
        return when (request.sakInformasjon.sakType) {
            YtelseType.ALDER,
            YtelseType.UFOREP,
            YtelseType.OMSORG -> Enhet.AUTOMATISK_JOURNALFORING
            else -> if (request.bosatt == Bosatt.NORGE) Enhet.NFP_UTLAND_AALESUND else Enhet.PENSJON_UTLAND
        }
    }

    /**
     * Henter enhet for [Relasjon.FORSORGER]
     *
     * @return Følger rutingregler i [enhetFraAlderOgLand] dersom sakinfo mangler eller sakstype er [YtelseType.GENRL],
     *  hvis ikke regnes saken som gyldig for [Enhet.AUTOMATISK_JOURNALFORING]
     */
    private fun enhetForRelasjonForsorger(request: OppgaveRoutingRequest): Enhet {
        return when (request.ytelseType) {
            null, YtelseType.GENRL -> enhetFraAlderOgLand(request)
            else -> Enhet.AUTOMATISK_JOURNALFORING
        }
    }

    /**
     * Sjekker om [YtelseType] er av en type som er godkjent for [Enhet.AUTOMATISK_JOURNALFORING]
     *
     * @return Boolean-verdi som indikerer om saken kan journalføres automatisk.
     */
    private fun journalforesAutomatisk(request: OppgaveRoutingRequest): Boolean {
        val sakInfo = request.sakInformasjon
        return (kanAutomatiskJournalfores(request) && sakInfo != null && sakInfo.harGenerellSakTypeMedTilknyttetSaker().not())
    }

    /**
     * Henter ut [Enhet] basert på gjeldende person sin bosetning og fødselsdato.
     * Se rutingregler her: {@see https://confluence.adeo.no/pages/viewpage.action?pageId=387092731}
     *
     * @return [Enhet] basert på rutingregler.
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
