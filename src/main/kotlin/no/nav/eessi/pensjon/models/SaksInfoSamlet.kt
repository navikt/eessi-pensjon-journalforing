package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon

data class SaksInfoSamlet(
    val saksIdFraSed: String? = null,
    val sakInformasjonFraPesys: SakInformasjon? = null,
    val saktypeFraSed: SakType? = null,
    val pesysSaker: List<String?> = emptyList(),
    val advarsel: Boolean = false,
)