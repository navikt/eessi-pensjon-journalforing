package no.nav.eessi.pensjon.services.norg2


enum class Diskresjonskode(val term: String) {
    KLIE("Klientadresse"),
    MILI("Militær"),
    PEND("Pendler"),
    SPFO("Sperret adresse, fortrolig"),
    SPSF("Sperret adresse, strengt fortrolig"),
    SVAL("Svalbard"),
    UFB("Uten fast bopel"),
    URIK("I utenrikstjeneste")
}