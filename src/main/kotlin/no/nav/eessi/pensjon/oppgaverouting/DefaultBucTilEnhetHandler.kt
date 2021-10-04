package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet

class DefaultBucTilEnhetHandler : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return if (request.harAdressebeskyttelse) {
            adresseBeskyttelseLogging(request.sedType, request.bucType, Enhet.DISKRESJONSKODE)
            Enhet.DISKRESJONSKODE
        }
        else
            enhetFraAlderOgLand(request)
    }

    private fun enhetFraAlderOgLand(request: OppgaveRoutingRequest): Enhet {
        val ageIsBetween18and62 = request.fdato.ageIsBetween18and62()

        return if (request.bosatt == Bosatt.NORGE) {
            if (ageIsBetween18and62) {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLANDSTILSNITT} på grunn av personen er bosatt i norge og alder er NAY")
                Enhet.UFORE_UTLANDSTILSNITT
            }
            else  {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.NFP_UTLAND_AALESUND} på grunn av personen er bosatt i utlandet og alder er NFP")
                Enhet.NFP_UTLAND_AALESUND
            }
        } else {
            if (ageIsBetween18and62) {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLAND} på grunn av personen er bosatt i utlandet og alder er NAY")
                Enhet.UFORE_UTLAND
            }
            else {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.PENSJON_UTLAND} på grunn av personen er bosatt i utlandet og alder er NFP")
                Enhet.PENSJON_UTLAND
            }
        }
    }
}
