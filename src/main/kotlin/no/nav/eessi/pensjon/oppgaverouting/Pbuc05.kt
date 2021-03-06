package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon

class Pbuc05 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return if (request.hendelseType == HendelseType.SENDT) enhetForSendt(request)
        else enhetForMottatt(request)
    }

    private fun enhetForSendt(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.sakInformasjon == null -> Enhet.ID_OG_FORDELING
            erGjenlevende(request.identifisertPerson) -> hentEnhetForGjenlevende(request)
            flerePersoner(request) -> hentEnhetForRelasjon(request)
            journalforesAutomatisk(request) -> Enhet.AUTOMATISK_JOURNALFORING
            else -> enhetFraAlderOgLand(request)
        }
    }

    private fun enhetForMottatt(request: OppgaveRoutingRequest): Enhet {
        val personListe = request.identifisertPerson?.personListe ?: emptyList()

        if (request.identifisertPerson?.personRelasjon?.fnr == null)
            return Enhet.ID_OG_FORDELING

        return if (personListe.isEmpty()) {
            if (erGjenlevende(request.identifisertPerson)) {
                if (request.bosatt == Bosatt.NORGE) Enhet.NFP_UTLAND_AALESUND
                else Enhet.PENSJON_UTLAND
            } else enhetFraAlderOgLand(request)
        } else if (personListe.isNotEmpty()) {
            when {
                personListe.any { it.personRelasjon.relasjon == Relasjon.FORSORGER } -> enhetFraAlderOgLand(request)
                personListe.any { it.personRelasjon.relasjon == Relasjon.BARN } -> enhetFraAlderOgLand(request)
                else -> Enhet.ID_OG_FORDELING
            }
        }
        else Enhet.ID_OG_FORDELING
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
            request.sakInformasjon.sakType == Saktype.GENRL -> enhetFraAlderOgLand(request)
            else -> Enhet.AUTOMATISK_JOURNALFORING
        }
    }

    /**
     * Sjekker om saken inneholder flere identifiserte personer.
     *
     * @return true dersom det finnes mer enn én person.
     */
    private fun flerePersoner(request: OppgaveRoutingRequest): Boolean {
        return request.identifisertPerson?.personListe?.isNotEmpty() ?: false
    }

    /**
     * Henter ut enhet basert på [Boolean].
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
     * @return Skal returnere [Enhet.AUTOMATISK_JOURNALFORING] dersom saktype er [Saktype.ALDER],
     *  [Saktype.UFOREP], eller [Saktype.OMSORG]. Hvis ikke skal forenklet rutingregel følges.
     */
    private fun enhetForRelasjonBarn(request: OppgaveRoutingRequest): Enhet {
        if (request.sakInformasjon?.sakId == null) return Enhet.ID_OG_FORDELING

        // I tilfeller hvor sakInformasjon er null
        return when (request.sakInformasjon.sakType) {
            Saktype.ALDER,
            Saktype.UFOREP,
            Saktype.OMSORG -> Enhet.AUTOMATISK_JOURNALFORING
            else -> if (request.bosatt == Bosatt.NORGE) Enhet.NFP_UTLAND_AALESUND else Enhet.PENSJON_UTLAND
        }
    }

    /**
     * Henter enhet for [Relasjon.FORSORGER]
     *
     * @return Følger rutingregler i [enhetFraAlderOgLand] dersom sakinfo mangler eller sakstype er [Saktype.GENRL],
     *  hvis ikke regnes saken som gyldig for [Enhet.AUTOMATISK_JOURNALFORING]
     */
    private fun enhetForRelasjonForsorger(request: OppgaveRoutingRequest): Enhet {
        return when (request.saktype) {
            null, Saktype.GENRL -> enhetFraAlderOgLand(request)
            else -> Enhet.AUTOMATISK_JOURNALFORING
        }
    }

    /**
     * Sjekker om [Saktype] er av en type som er godkjent for [Enhet.AUTOMATISK_JOURNALFORING]
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
        val navArbeidOgYtelserForTyskland = request.fdato.ageIsBetween18and60()
        val navArbeidOgYtelser = request.fdato.ageIsBetween18and62()

        val ytelse = if (request.avsenderLand == "DE") {
            navArbeidOgYtelserForTyskland
        } else {
            navArbeidOgYtelser
        }

        return if (request.bosatt == Bosatt.NORGE) {
            if (ytelse) Enhet.UFORE_UTLANDSTILSNITT
            else Enhet.NFP_UTLAND_AALESUND
        } else {
            if (ytelse) Enhet.UFORE_UTLAND
            else Enhet.PENSJON_UTLAND
        }
    }
}
