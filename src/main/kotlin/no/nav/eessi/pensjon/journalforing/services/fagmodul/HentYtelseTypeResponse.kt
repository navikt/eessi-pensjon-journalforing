package no.nav.eessi.pensjon.journalforing.services.fagmodul


import com.fasterxml.jackson.annotation.JsonProperty

data class HentYtelseTypeResponse(
        var kravDato: String?,
        var type: YtelseType
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
