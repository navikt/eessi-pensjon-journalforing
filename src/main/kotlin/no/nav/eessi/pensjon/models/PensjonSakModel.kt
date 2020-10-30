package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

//class PensjonSakInformasjon(
//        private val sakInformasjon: SakInformasjon? = null
//) {
//    fun getSakId() = sakInformasjon?.sakId
//
//    fun getSakType() = sakInformasjon?.sakType
//
//    fun getSakStatus() = sakInformasjon?.sakStatus
//
//    fun isNotNull() = sakInformasjon != null
//
//    companion object {
//        fun from (bestemSak: SakInformasjon?, pensjonSed: SakInformasjon?) :PensjonSakInformasjon {
//            return PensjonSakInformasjon(bestemSak ?: pensjonSed)
//        }
//    }
//}

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
    LOPENDE
}