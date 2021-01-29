package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.YtelseType

class Pbuc10 : BucTilEnhetHandler {

    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.harAdressebeskyttelse -> Enhet.DISKRESJONSKODE
            erSakUgyldig(request) -> Enhet.ID_OG_FORDELING
            kanAutomatiskJournalfores(request) -> Enhet.AUTOMATISK_JOURNALFORING
            else -> enhetFraLand(request)
        }
    }

    private fun enhetFraLand(request: OppgaveRoutingRequest): Enhet {
        return if (request.bosatt == Bosatt.NORGE) routeNorge(request)
        else routeUtland(request.ytelseType)
    }

    private fun routeNorge(request: OppgaveRoutingRequest): Enhet {
        if (erMottattAlderEllerGjenlev(request))
            return Enhet.NFP_UTLAND_AALESUND

        return when (request.ytelseType) {
            YtelseType.UFOREP -> Enhet.UFORE_UTLANDSTILSNITT
            else -> Enhet.ID_OG_FORDELING
        }
    }

    private fun routeUtland(ytelseType: YtelseType?): Enhet {
        return if (ytelseType == YtelseType.UFOREP) Enhet.UFORE_UTLAND
        else Enhet.PENSJON_UTLAND
    }

    private fun erSakUgyldig(request: OppgaveRoutingRequest): Boolean {
        if (request.hendelseType ==  HendelseType.SENDT) {
            return request.run {
                identifisertPerson?.personRelasjon?.ytelseType == YtelseType.GJENLEV
                        && ytelseType == YtelseType.UFOREP
                        && sakInformasjon?.sakStatus == SakStatus.AVSLUTTET
                        && sakInformasjon.sakType == YtelseType.UFOREP
            }
        }
        return false
    }

    private fun erMottattAlderEllerGjenlev(request: OppgaveRoutingRequest): Boolean {
        return request.run {
            hendelseType == HendelseType.MOTTATT
                    && (ytelseType == YtelseType.ALDER || request.ytelseType == YtelseType.GJENLEV)
        }
    }
}
