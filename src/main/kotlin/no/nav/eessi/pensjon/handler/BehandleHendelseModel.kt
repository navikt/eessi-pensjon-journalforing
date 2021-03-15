package no.nav.eessi.pensjon.handler

data class BehandleHendelseModel(
    var sakId: String? = null,
    var bucId: String? = null,
    var hendelsesKode: HendelseKode,
    var beskrivelse: String? = null
)

enum class HendelseKode {
    SOKNAD_OM_ALDERSPENSJON,
    SOKNAD_OM_UFORE,
    INFORMASJON_FRA_UTLANDET,
    UKJENT
}