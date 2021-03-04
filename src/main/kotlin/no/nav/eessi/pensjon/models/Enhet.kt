package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

enum class Enhet(
        @JsonValue val enhetsNr: String
) {
    PENSJON_UTLAND("0001"),
    UFORE_UTLANDSTILSNITT("4476"),
    UFORE_UTLAND("4475"),
    NFP_UTLAND_AALESUND("4862"),
    NFP_UTLAND_OSLO("4803"),
    ID_OG_FORDELING("4303"),
    DISKRESJONSKODE("2103"), //Vikafossen strengt fortrolig SPSF
    OKONOMI_PENSJON("4819"),
    AUTOMATISK_JOURNALFORING("9999"),
    UGYLDIG_ARKIV_TYPE(""); //må være blank for oppgave støtter ikke enhetnr 9999.


    companion object {
        fun getEnhet(enhetsNr: String): Enhet? = values().find { it.enhetsNr == enhetsNr }
    }
}