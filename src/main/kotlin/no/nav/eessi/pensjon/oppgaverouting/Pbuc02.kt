package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype

/**
 * P_BUC_02: Krav om etterlatteytelser
 *
 * @see <a href="https://jira.adeo.no/browse/EP-853">Jira-sak EP-853</a>
 */
class Pbuc02 : BucTilEnhetHandler {

    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.harAdressebeskyttelse -> {
                adresseBeskyttelseLogging(request.sedType, request.bucType, Enhet.DISKRESJONSKODE)
                Enhet.DISKRESJONSKODE
            }
            erUforeSakAvsluttet(request) -> {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av uføresak er avsluttet")
                Enhet.ID_OG_FORDELING
            }
            kanAutomatiskJournalfores(request) -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
            request.bosatt == Bosatt.NORGE -> {
                when (request.saktype) {
                    Saktype.UFOREP -> {
                        bosattNorgeLogging(request.sedType, request. bucType, request.saktype, Enhet.UFORE_UTLANDSTILSNITT)
                        Enhet.UFORE_UTLANDSTILSNITT
                    }
                    Saktype.ALDER -> {
                        bosattNorgeLogging(request.sedType, request. bucType, request.saktype, Enhet.NFP_UTLAND_AALESUND)
                        Enhet.NFP_UTLAND_AALESUND
                    }
                    Saktype.BARNEP,
                    Saktype.GJENLEV -> {
                        bosattNorgeLogging(request.sedType, request. bucType, request.saktype, Enhet.PENSJON_UTLAND)
                        Enhet.PENSJON_UTLAND
                    }
                    else -> {
                        logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av bosatt norge med ugyldig saktype")
                        Enhet.ID_OG_FORDELING
                    }
                }
            }
            else ->
                when (request.saktype) {
                    Saktype.UFOREP -> {
                        bosattUtlandLogging(request.sedType, request.bucType, request.saktype, Enhet.UFORE_UTLAND)
                        Enhet.UFORE_UTLAND
                    }
                    Saktype.ALDER,
                    Saktype.BARNEP,
                    Saktype.GJENLEV -> {
                        bosattUtlandLogging(request.sedType, request. bucType, request.saktype, Enhet.PENSJON_UTLAND)
                        Enhet.PENSJON_UTLAND
                    }
                    else -> {
                        logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av bosatt utland med ugyldig saktype")
                        Enhet.ID_OG_FORDELING
                    }
                }
        }
    }

    private fun erUforeSakAvsluttet(request: OppgaveRoutingRequest): Boolean {
        val sakInfo = request.sakInformasjon
        val erUforepensjon = (request.saktype == Saktype.UFOREP || sakInfo?.sakType == Saktype.UFOREP)

        return erUforepensjon && sakInfo?.sakStatus == SakStatus.AVSLUTTET
    }
}
