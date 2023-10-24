package no.nav.eessi.pensjon.klienter.navansatt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Navansatt (
    val navn: String,
    val groups: List<String>
)

data class EnheterFraAd (
    val enheter: List<Enheter>? = null
)

data class Enheter (
    val id: String? = null,
    val navn: String? = null,
    val nivaa: String? = null
)