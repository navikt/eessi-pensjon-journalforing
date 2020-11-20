package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType

class Pbuc02 : BucTilEnhetHandler() {

    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            automatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            request.bosatt == Bosatt.NORGE -> handleNorge(request)
            else -> handleUtland(request)
        }
    }

    private fun handleNorge(request: OppgaveRoutingRequest): Enhet =
            when (request.ytelseType) {
                YtelseType.UFOREP -> if (request.sakStatus != SakStatus.AVSLUTTET) Enhet.UFORE_UTLANDSTILSNITT else Enhet.ID_OG_FORDELING
                YtelseType.ALDER -> Enhet.NFP_UTLAND_AALESUND
                YtelseType.BARNEP -> Enhet.NFP_UTLAND_AALESUND
                YtelseType.GJENLEV -> Enhet.NFP_UTLAND_AALESUND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun handleUtland(request: OppgaveRoutingRequest): Enhet =
            when (request.ytelseType) {
                YtelseType.UFOREP -> if (request.sakStatus != SakStatus.AVSLUTTET) Enhet.UFORE_UTLAND else Enhet.ID_OG_FORDELING
                YtelseType.ALDER -> Enhet.PENSJON_UTLAND
                YtelseType.BARNEP -> Enhet.PENSJON_UTLAND
                YtelseType.GJENLEV -> Enhet.PENSJON_UTLAND
                else -> Enhet.ID_OG_FORDELING
            }

    private fun automatiskJournalfores(request: OppgaveRoutingRequest
    ): Boolean {
        return (kanAutomatiskJournalfores(request) && (request.hendelseType == HendelseType.MOTTATT && (request.ytelseType == YtelseType.UFOREP && request.sakStatus == SakStatus.AVSLUTTET).not()
                || ( request.hendelseType == HendelseType.SENDT)))
    }
}
