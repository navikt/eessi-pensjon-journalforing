package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
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
            erStrengtFortrolig(request.diskresjonskode) -> Enhet.DISKRESJONSKODE
            erUgyldig(request.sakInformasjon) -> Enhet.ID_OG_FORDELING
            automatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
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

    private fun automatiskJournalfores(request: OppgaveRoutingRequest): Boolean {
        return if (request.hendelseType == HendelseType.MOTTATT) false
        else kanAutomatiskJournalfores(request)
    }

    private fun erUgyldig(sakInfo: SakInformasjon?): Boolean =
            sakInfo?.sakType == YtelseType.UFOREP && sakInfo.sakStatus == SakStatus.AVSLUTTET
}
