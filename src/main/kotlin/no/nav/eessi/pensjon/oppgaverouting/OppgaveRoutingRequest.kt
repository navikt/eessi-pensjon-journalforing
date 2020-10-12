package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import java.time.LocalDate

class OppgaveRoutingRequest(
        val fnr: String? = null,
        val fdato: LocalDate,
        val diskresjonskode: String? = null,
        val landkode: String? = null,
        val geografiskTilknytning: String? = null,
        val ytelseType: YtelseType? = null,
        val sedType: SedType? = null,
        val hendelseType: HendelseType? = null,
        val sakStatus: SakStatus? = null,
        val identifisertPerson: IdentifisertPerson? = null,
        val bucType: BucType? = null
) {
    val bosatt = Bosatt.fraLandkode(landkode)
}
