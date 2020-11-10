package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class SakInformasjon(
        val sakId: String,
        val sakType: YtelseType,
        val sakStatus: SakStatus) {

    var saksbehandlendeEnhetId = ""
    var nyopprettet = false

   constructor(
            sakId: String,
            sakType: YtelseType,
            sakStatus: SakStatus,
            saksbehandlendeEnhetId: String,
            nyopprettet: Boolean): this(sakId, sakType, sakStatus) {

       this.saksbehandlendeEnhetId = saksbehandlendeEnhetId
       this.nyopprettet = nyopprettet
   }
}


enum class SakStatus {
    OPPRETTET,
    TIL_BEHANDLING,
    AVSLUTTET,
    LOPENDE,
    OPPHOR
}