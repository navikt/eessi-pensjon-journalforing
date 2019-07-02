package no.nav.eessi.pensjon.journalforing.services.fagmodul

import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel


class HentYtelseTypeMapper {
    fun map(hentYtelseTypeResponse: HentYtelseTypeResponse) : OppgaveRoutingModel.YtelseType {
        return when(hentYtelseTypeResponse.type) {
            HentYtelseTypeResponse.YtelseType.AP -> OppgaveRoutingModel.YtelseType.AP
            HentYtelseTypeResponse.YtelseType.GP -> OppgaveRoutingModel.YtelseType.GP
            HentYtelseTypeResponse.YtelseType.UT -> OppgaveRoutingModel.YtelseType.UT
        }
    }
 }