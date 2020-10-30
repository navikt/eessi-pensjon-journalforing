package no.nav.eessi.pensjon.models


class PensjonSak(
        val sakid: Long,
        val sakType: YtelseType,
        val status: String
) {
    override fun toString(): String {
        return "$sakid, $sakType, $status"
    }
}

class PensjonSakInformasjon(
        val sakInformasjon: SakInformasjon? = null,
        val pensjonSak: PensjonSak? = null
) {
    fun getSakId(): String? = sakInformasjon?.sakId ?: pensjonSak?.sakid?.toString()
}

class SakInformasjon(val sakId: String,
                     val sakType: YtelseType,
                     val sakStatus: SakStatus,
                     val saksbehandlendeEnhetId: String,
                     val nyopprettet: Boolean)

enum class SakStatus {
    OPPRETTET,
    TIL_BEHANDLING,
    AVSLUTTET,
    LOPENDE
}