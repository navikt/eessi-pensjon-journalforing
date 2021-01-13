package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType

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
            request.bosatt == Bosatt.NORGE -> handleNorge(request.ytelseType)
            else -> handleUtland(request.ytelseType)
        }
    }

    private fun handleNorge(ytelseType: YtelseType?): Enhet =
            when (ytelseType) {
                YtelseType.UFOREP -> Enhet.UFORE_UTLANDSTILSNITT
                YtelseType.ALDER,
                YtelseType.BARNEP,
                YtelseType.GJENLEV -> Enhet.NFP_UTLAND_AALESUND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun handleUtland(ytelseType: YtelseType?): Enhet =
            when (ytelseType) {
                YtelseType.UFOREP -> Enhet.UFORE_UTLAND
                YtelseType.ALDER,
                YtelseType.BARNEP,
                YtelseType.GJENLEV -> Enhet.PENSJON_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun erUgyldig(request: OppgaveRoutingRequest): Boolean {
        val sakInfo = request.sakInformasjon
        val erUforepensjon = (request.ytelseType == YtelseType.UFOREP || sakInfo?.sakType == YtelseType.UFOREP)

        return erUforepensjon && sakInfo?.sakStatus == SakStatus.AVSLUTTET
    }
}
