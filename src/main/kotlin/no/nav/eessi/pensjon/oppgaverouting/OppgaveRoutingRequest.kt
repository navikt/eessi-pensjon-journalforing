package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.sed.SedHendelseModel
import java.time.LocalDate

class OppgaveRoutingRequest(
        val fnr: String? = null,
        val fdato: LocalDate,
        val diskresjonskode: Diskresjonskode? = null,
        val landkode: String? = null,
        val geografiskTilknytning: String? = null,
        val ytelseType: YtelseType? = null,
        val sedType: SedType? = null,
        val hendelseType: HendelseType? = null,
        val sakInformasjon: SakInformasjon? = null,
        val identifisertPerson: IdentifisertPerson? = null,
        val bucType: BucType? = null
) {
    val bosatt = Bosatt.fraLandkode(landkode)

    companion object {
        fun fra(
                identifisertPerson: IdentifisertPerson?,
                fdato: LocalDate,
                ytelseType: YtelseType?,
                sedHendelseModel: SedHendelseModel,
                hendelseType: HendelseType,
                sakInformasjon: SakInformasjon?
        ): OppgaveRoutingRequest {
            return OppgaveRoutingRequest(
                    identifisertPerson?.aktoerId,
                    fdato,
                    identifisertPerson?.diskresjonskode,
                    identifisertPerson?.landkode,
                    identifisertPerson?.geografiskTilknytning,
                    ytelseType,
                    sedHendelseModel.sedType,
                    hendelseType,
                    sakInformasjon,
                    identifisertPerson,
                    sedHendelseModel.bucType
            )
        }
    }
}
