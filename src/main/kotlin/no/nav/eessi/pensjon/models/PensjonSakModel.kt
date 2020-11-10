package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class SakInformasjon(
        val sakId: String,
        val sakType: YtelseType,
        val sakStatus: SakStatus,
        val saksbehandlendeEnhetId: String = "",
        val nyopprettet: Boolean = false
)

enum class SakStatus {
    OPPRETTET,
    TIL_BEHANDLING,
    AVSLUTTET,
    LOPENDE
}