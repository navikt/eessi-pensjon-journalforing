package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType

class Pbuc02 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet =
            if (request.bosatt == Bosatt.NORGE)
                handleNorge(request.ytelseType, request.sakInformasjon?.sakStatus)
            else
                handleUtland(request.ytelseType, request.sakInformasjon?.sakStatus)

    private fun handleNorge(ytelseType: YtelseType?, sakStatus: SakStatus?): Enhet =
            when (ytelseType) {
                YtelseType.UFOREP -> if (sakStatus != SakStatus.AVSLUTTET) Enhet.UFORE_UTLANDSTILSNITT else Enhet.ID_OG_FORDELING
                YtelseType.ALDER -> Enhet.NFP_UTLAND_AALESUND
                YtelseType.BARNEP -> Enhet.NFP_UTLAND_AALESUND
                YtelseType.GJENLEV -> Enhet.NFP_UTLAND_AALESUND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun handleUtland(ytelseType: YtelseType?, sakStatus: SakStatus?): Enhet =
            when (ytelseType) {
                YtelseType.UFOREP -> if (sakStatus != SakStatus.AVSLUTTET) Enhet.UFORE_UTLAND else Enhet.ID_OG_FORDELING
                YtelseType.ALDER -> Enhet.PENSJON_UTLAND
                YtelseType.BARNEP -> Enhet.PENSJON_UTLAND
                YtelseType.GJENLEV -> Enhet.PENSJON_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }
}
