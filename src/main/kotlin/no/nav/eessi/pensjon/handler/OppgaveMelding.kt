package no.nav.eessi.pensjon.handler

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.eux.model.sed.SedType

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppgaveMelding(
        val sedType : SedType?,
        val journalpostId : String? = null,
        val tildeltEnhetsnr : Enhet,
        val aktoerId : String?,
        val rinaSakId : String,
        val hendelseType : HendelseType,
        var filnavn : String?
    )  {

    @JsonProperty("oppgaveType")
    fun oppgaveType(): String {
        return if (journalpostId == null) "BEHANDLE_SED" else "JOURNALFORING"
    }

    override fun toString(): String {
        return toJson()
    }
}
