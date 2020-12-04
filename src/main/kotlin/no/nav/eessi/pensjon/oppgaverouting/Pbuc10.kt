package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType

class Pbuc10 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            erStrengtFortrolig(request.diskresjonskode) -> Enhet.DISKRESJONSKODE
            kanAutomatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            else -> enhetFraAlderOgLand(request)
        }
    }

    override fun kanAutomatiskJournalfores(request: OppgaveRoutingRequest): Boolean {
        val sakInformasjon = request.sakInformasjon
        if (request.ytelseType == YtelseType.UFOREP && request.hendelseType == HendelseType.SENDT && sakInformasjon?.sakStatus == SakStatus.AVSLUTTET && sakInformasjon.sakType == YtelseType.UFOREP) {
            return false
        }
        return super.kanAutomatiskJournalfores(request)
    }

    private fun enhetFraAlderOgLand(request: OppgaveRoutingRequest): Enhet {
        if (request.ytelseType == YtelseType.UFOREP && request.hendelseType == HendelseType.SENDT && request.sakInformasjon?.sakStatus == SakStatus.AVSLUTTET && request.sakInformasjon.sakType == YtelseType.UFOREP) return Enhet.ID_OG_FORDELING
        return if (request.hendelseType == HendelseType.MOTTATT) {
            routingMottatt(request)
        } else {
            routingSendt(request)
        }
    }

    private fun routingMottatt(request: OppgaveRoutingRequest): Enhet {
        return when (request.bosatt) {
            Bosatt.NORGE -> {
                when (request.ytelseType) {
                    YtelseType.UFOREP -> Enhet.UFORE_UTLANDSTILSNITT
                    YtelseType.ALDER, YtelseType.GJENLEV -> Enhet.NFP_UTLAND_AALESUND
                    else -> Enhet.ID_OG_FORDELING
                }
            }
            else -> {
                if (request.ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLAND
                else Enhet.PENSJON_UTLAND
            }
        }

    }

    private fun routingSendt(request: OppgaveRoutingRequest): Enhet {
        return when (request.bosatt) {
            Bosatt.NORGE -> {
                when (request.ytelseType) {
                    YtelseType.UFOREP -> Enhet.UFORE_UTLANDSTILSNITT
                    else -> Enhet.ID_OG_FORDELING
                }
            }
            else -> {
                if (request.ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLAND
                else Enhet.PENSJON_UTLAND
            }
        }

    }
}

