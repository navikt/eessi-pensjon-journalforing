package no.nav.eessi.pensjon.personidentifisering.services


enum class Diskresjonskode(val term: String) {
    SPFO("Sperret adresse, fortrolig"),
    SPSF("Sperret adresse, strengt fortrolig"),
}