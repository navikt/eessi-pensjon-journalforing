package no.nav.eessi.pensjon.services.fagmodul


import com.fasterxml.jackson.annotation.JsonProperty

class HentPinOgYtelseTypeResponse(
        var fnr: String?,
        var krav: Krav?
        )
class Krav(
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
