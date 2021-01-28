package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SakInformasjon(
        val sakId: String?,
        val sakType: YtelseType,
        val sakStatus: SakStatus,
        val saksbehandlendeEnhetId: String = "",
        val nyopprettet: Boolean = false,

        @JsonIgnore
        val tilknyttedeSaker: List<SakInformasjon> = emptyList()
) {
    fun harGenerellSakTypeMedTilknyttetSaker() : Boolean {
        return sakType == YtelseType.GENRL && tilknyttedeSaker.isNotEmpty()
    }
}

enum class SakStatus {
    OPPRETTET,
    TIL_BEHANDLING,
    AVSLUTTET,
    LOPENDE,
    OPPHOR,
    UKJENT
}