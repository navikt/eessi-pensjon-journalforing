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
            request.harAdressebeskyttelse -> Enhet.DISKRESJONSKODE
            erUgyldig(request) -> Enhet.ID_OG_FORDELING
            kanAutomatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            request.bosatt == Bosatt.NORGE -> handleNorge(request.saktype)
            else -> handleUtland(request.saktype)
        }
    }

    private fun handleNorge(saktype: Saktype?): Enhet =
            when (saktype) {
                Saktype.UFOREP -> Enhet.UFORE_UTLANDSTILSNITT
                Saktype.ALDER -> Enhet.NFP_UTLAND_AALESUND
                Saktype.BARNEP,
                Saktype.GJENLEV -> Enhet.PENSJON_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun handleUtland(saktype: Saktype?): Enhet =
            when (saktype) {
                Saktype.UFOREP -> Enhet.UFORE_UTLAND
                Saktype.ALDER,
                Saktype.BARNEP,
                Saktype.GJENLEV -> Enhet.PENSJON_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun erUgyldig(request: OppgaveRoutingRequest): Boolean {
        val sakInfo = request.sakInformasjon
        val erUforepensjon = (request.saktype == Saktype.UFOREP || sakInfo?.sakType == Saktype.UFOREP)

        return erUforepensjon && sakInfo?.sakStatus == SakStatus.AVSLUTTET
    }
}
