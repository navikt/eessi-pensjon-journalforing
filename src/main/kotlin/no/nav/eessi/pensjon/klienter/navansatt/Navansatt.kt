package no.nav.eessi.pensjon.klienter.navansatt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.oppgaverouting.Enhet

@JsonIgnoreProperties(ignoreUnknown = true)
data class Navansatt (
    val navn: String,
    val groups: List<String>
)

data class EnheterFraAd (
    val id: String? = null,
    val navn: String? = null,
    val nivaa: String? = null
)