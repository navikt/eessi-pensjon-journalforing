package no.nav.eessi.pensjon.services.person


enum class Diskresjonskode(val term: String) {
    SPFO("Sperret adresse, fortrolig"),
    SPSF("Sperret adresse, strengt fortrolig"),
}