package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

/**
 * P_BUC_04: Anmodning  om opplysninger om perioder med omsorg for barn
 */
class Pbuc04 : BucTilEnhetHandler {
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
}
