package no.nav.eessi.pensjon.sed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType

@JsonIgnoreProperties(ignoreUnknown = true)
data class SedHendelseModel(
        val id: Long? = 0,
        val sedId: String? = null,
        val sektorKode: String,
        val bucType: BucType?,
        val rinaSakId: String,
        val avsenderId: String? = null,
        val avsenderNavn: String? = null,
        val avsenderLand: String? = null,
        val mottakerId: String? = null,
        val mottakerNavn: String? = null,
        val mottakerLand: String? = null,
        val rinaDokumentId: String,
        val rinaDokumentVersjon: String? = null,
        val sedType: SedType? = null,
        val navBruker: String? = null
) {
    companion object {
        fun fromJson(json: String): SedHendelseModel = mapJsonToAny(json, typeRefs())
    }
}
