package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import java.time.LocalDate

class OppgaveRoutingRequest(
    val aktorId: String? = null,
    val fdato: LocalDate,
    val harAdressebeskyttelse: Boolean = false,
    val landkode: String? = null,
    val geografiskTilknytning: String? = null,
    val saktype: Saktype? = null,
    val sedType: SedType? = null,
    val hendelseType: HendelseType,
    val sakInformasjon: SakInformasjon? = null,
    val identifisertPerson: IdentifisertPerson? = null,
    val bucType: BucType,
    val avsenderLand: String? = null
) {
    val bosatt = Bosatt.fraLandkode(landkode)

    companion object {
        fun fra(
            identifisertPerson: IdentifisertPerson?,
            fdato: LocalDate,
            saktype: Saktype?,
            sedHendelseModel: SedHendelseModel,
            hendelseType: HendelseType,
            sakInformasjon: SakInformasjon?,
            harAdressebeskyttelse: Boolean = false
        ): OppgaveRoutingRequest {
            return OppgaveRoutingRequest(
                    identifisertPerson?.aktoerId,
                    fdato,
                    harAdressebeskyttelse,
                    identifisertPerson?.landkode,
                    identifisertPerson?.geografiskTilknytning,
                    saktype,
                    sedHendelseModel.sedType,
                    hendelseType,
                    sakInformasjon,
                    identifisertPerson,
                    sedHendelseModel.bucType!!,
                    sedHendelseModel.avsenderLand
            )
        }
    }
}
