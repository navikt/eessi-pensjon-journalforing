package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

/**
 * P_BUC_01: Krav om alderspensjon
 */
class Pbuc01 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.harAdressebeskyttelse -> {
                adresseBeskyttelseLogging(request.sedType, request.bucType, Enhet.DISKRESJONSKODE)
                Enhet.DISKRESJONSKODE
            }
            kanAutomatiskJournalfores(request) -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
            request.bosatt == Bosatt.NORGE -> {
                bosattNorgeLogging(request.sedType, request.bucType, Enhet.NFP_UTLAND_AALESUND)
                Enhet.NFP_UTLAND_AALESUND
            }
            else -> {
                ingenSærreglerLogging(request.sedType, request.bucType, Enhet.PENSJON_UTLAND)
                Enhet.PENSJON_UTLAND
            }
        }
    }

    override fun kanAutomatiskJournalfores(request: OppgaveRoutingRequest): Boolean {
        return request.run {
            saktype != null
                    && !aktorId.isNullOrBlank()
                    && !sakInformasjon?.sakId.isNullOrBlank()
        }
    }
}
