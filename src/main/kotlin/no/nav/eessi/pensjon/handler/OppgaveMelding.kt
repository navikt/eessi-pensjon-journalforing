package no.nav.eessi.pensjon.handler

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppgaveMelding(
    val sedType: SedType?,
    val journalpostId: String? = null,
    val tildeltEnhetsnr: Enhet,
    val aktoerId: String?,
    val rinaSakId: String,
    val hendelseType: HendelseType,
    var filnavn: String?,
    val oppgaveType: OppgaveType
)  {

    override fun toString(): String {
        return toJson()
    }

}
enum class OppgaveType{
    BEHANDLE_SED,
    JOURNALFORING,
    KRAV; //støtter ikke tildeltenhet 9999
}
