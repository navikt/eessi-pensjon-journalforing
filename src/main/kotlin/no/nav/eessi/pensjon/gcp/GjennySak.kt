package no.nav.eessi.pensjon.gcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GjennySak (
    val sakId: String? = null,
    val sakType: String
)
