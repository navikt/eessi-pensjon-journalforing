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
            request.sakInformasjon == null -> {
                logger.info("Router sendt ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av manglende saksinformasjon")
                Enhet.ID_OG_FORDELING
            }
            erGjenlevende(request.identifisertPerson) -> hentEnhetForGjenlevende(request)
            flerePersoner(request) -> hentEnhetForRelasjon(request)
            kanJournalforesAutomatisk(request) -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
            else -> enhetFraAlderOgLand(request)
        }
    }

    private fun enhetForMottatt(request: OppgaveRoutingRequest): Enhet {
        val personListe = request.identifisertPerson?.personListe ?: emptyList()

        if (request.identifisertPerson?.personRelasjon?.fnr == null) {
            logger.info("Router mottatt ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av manglende fødselsnummer")
            return Enhet.ID_OG_FORDELING
        }

        return if (personListe.isEmpty()) {
            if (erGjenlevende(request.identifisertPerson)) {
                if (request.bosatt == Bosatt.NORGE) {
                    logger.info("Router mottatt ${request.sedType} i ${request.bucType} til ${Enhet.NFP_UTLAND_AALESUND.enhetsNr} på grunn av bosatt Norge og person er gjenlevende")
                    Enhet.NFP_UTLAND_AALESUND
                }
                else {
                    logger.info("Router mottatt ${request.sedType} i ${request.bucType} til ${Enhet.PENSJON_UTLAND.enhetsNr} på grunn av bosatt Norge og person er ikke gjenlevende")
                    Enhet.PENSJON_UTLAND
                }
            } else enhetFraAlderOgLand(request)
        } else {
            when {
                personListe.any { it.personRelasjon.relasjon == Relasjon.FORSORGER } -> enhetFraAlderOgLand(request)
                personListe.any { it.personRelasjon.relasjon == Relasjon.BARN } -> enhetFraAlderOgLand(request)
                else -> {
                    logger.info("Router mottatt ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av det finnes personrelasjoner men disse er hverken forsørger eller barn")
                    Enhet.ID_OG_FORDELING
                }
            }
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
            request.sakInformasjon == null -> {
                logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING} på grunn av personen er gjenlevende men saksinformasjon mangler")
                Enhet.ID_OG_FORDELING
            }
            request.sakInformasjon.sakType == Saktype.GENRL -> enhetFraAlderOgLand(request)
            else -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
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
        if (request.sakInformasjon?.sakId == null) {
            logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av manglende pesys saksId")
            return Enhet.ID_OG_FORDELING
        }

        return when (request.sakInformasjon.sakType) {
            Saktype.ALDER,
            Saktype.UFOREP,
            Saktype.OMSORG -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
            else -> if (request.bosatt == Bosatt.NORGE) {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.NFP_UTLAND_AALESUND.enhetsNr} på grunn av bosatt norge med personrelasjon: Barn ")
                Enhet.NFP_UTLAND_AALESUND
            }
            else {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.PENSJON_UTLAND.enhetsNr} på grunn av bosatt utland med personrelasjon: Barn ")
                Enhet.PENSJON_UTLAND
            }
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
            else -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
        }
    }

    /**
     * Sjekker om [Saktype] er av en type som er godkjent for [Enhet.AUTOMATISK_JOURNALFORING]
     *
     * @return Boolean-verdi som indikerer om saken kan journalføres automatisk.
     */
    private fun kanJournalforesAutomatisk(request: OppgaveRoutingRequest): Boolean {
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
        return if (request.bosatt == Bosatt.NORGE) {
            if (request.avsenderLand == "DE" && request.fdato.ageIsBetween18and60()) {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLANDSTILSNITT.enhetsNr} på grunn av bosatt norge, avsenderland er DE og alder er mellom 18 og 60")
                Enhet.UFORE_UTLANDSTILSNITT
            }
            else if (request.avsenderLand != "DE" && request.fdato.ageIsBetween18and62()) {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLANDSTILSNITT.enhetsNr} på grunn av bosatt norge, avsenderland ikke er DE og alder er mellom 18 og 62")
                Enhet.UFORE_UTLANDSTILSNITT
            }
            else {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.NFP_UTLAND_AALESUND.enhetsNr} på grunn av bosatt norge og alder er NFP")
                Enhet.NFP_UTLAND_AALESUND
            }
        } else {
            if (request.fdato.ageIsBetween18and62()) {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLAND.enhetsNr} på grunn av bosatt utland og alder er NAY")
                Enhet.UFORE_UTLAND
            }
            else {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.PENSJON_UTLAND.enhetsNr} på grunn av bosatt utland og alder er NFP")
                Enhet.PENSJON_UTLAND
            }
        }
    }
}
