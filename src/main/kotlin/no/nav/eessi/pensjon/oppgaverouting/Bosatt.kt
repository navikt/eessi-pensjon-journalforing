package no.nav.eessi.pensjon.oppgaverouting

enum class Bosatt {
    NORGE,
    UTLAND,
    UKJENT;

    companion object {
        fun fraLandkode(landkode: String?) =
                when {
                    landkode.isNullOrEmpty() -> UKJENT
                    landkode == "NOR" -> NORGE
                    else -> UTLAND
                }
    }
}
