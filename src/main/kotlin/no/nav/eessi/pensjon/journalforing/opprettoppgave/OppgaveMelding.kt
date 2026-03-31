package no.nav.eessi.pensjon.journalforing.opprettoppgave

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.utils.toJson

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppgaveMelding(
    val sedType: SedType?,
    val journalpostId: String? = null,
    val tildeltEnhetsnr: Enhet,
    val aktoerId: String?,
    val rinaSakId: String,
    val hendelseType: HendelseType,
    var filnavn: String?,
    val oppgaveType: OppgaveType,
    val tema: Tema? = Tema.PENSJON,
    val sendeAdvarsel: Boolean? = false,
    val beskrivelse: String? = null,
)  {
    override fun toString(): String {
        return toJson()
    }

}

data class OppdaterOppgaveMelding(
    val id: String,
    val status: String,
    val tildeltEnhetsnr: Enhet,
    val tema: String,
    val aktoerId: String?,
    val rinaSakId: String
    )

data class OppgaveBruker(
    val ident: String,
    val type: OppgaveBrukerType
)

enum class OppgaveBrukerType {
    PERSON, ARBEIDSGIVER, SAMHANDLER
}

enum class OppgaveType{
    BEHANDLE_SED,
    JOURNALFORING_UT,
    JOURNALFORING,
    KRAV; //støtter ikke tildeltenhet 9999
}
