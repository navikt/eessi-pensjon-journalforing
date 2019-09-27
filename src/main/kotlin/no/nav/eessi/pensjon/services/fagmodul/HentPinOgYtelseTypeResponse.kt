package no.nav.eessi.pensjon.services.fagmodul


import com.fasterxml.jackson.annotation.JsonProperty

data class HentPinOgYtelseTypeResponse(
        var fnr: String?,
        var krav: Krav?
        )
data class Krav(
        var dato: String?,
        var type: YtelseType?
) {

    enum class YtelseType {
        @JsonProperty("01")
        AP,
        @JsonProperty("02")
        GP,
        @JsonProperty("03")
        UT
    }
}
