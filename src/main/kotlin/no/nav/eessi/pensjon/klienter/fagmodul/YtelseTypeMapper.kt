package no.nav.eessi.pensjon.klienter.fagmodul

class YtelseTypeMapper {
    fun map(hentYtelseTypeResponse: HentYtelseTypeResponse?) : String? {
        return when(hentYtelseTypeResponse?.krav?.type) {
            Krav.YtelseType.AP -> "AP"
            Krav.YtelseType.GP -> "GP"
            Krav.YtelseType.UT -> "UT"
            null -> null
        }
    }
}