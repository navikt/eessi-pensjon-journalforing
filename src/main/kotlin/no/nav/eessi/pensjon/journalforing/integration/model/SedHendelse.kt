package no.nav.eessi.pensjon.journalforing.integration.model

data class SedHendelse (

    val id: Long? = 0,
    val sedId: String? = null,
    val sektorKode: String? = null,
    val bucType: String? = null,
    val rinaSakId: String? = null,
    val avsenderId: String? = null,
    val avsenderNavn: String? = null,
    val mottakerId: String? = null,
    val mottakerNavn: String? = null,
    val rinaDokumentId: String? = null,
    val rinaDokumentVersjon: String? = null,
    val sedType: String? = null,
    val navBruker: String? = null
)