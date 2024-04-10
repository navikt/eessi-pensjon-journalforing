package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon

data class SaksInfoSamlet(
    val saksIdFraSed: String? = null,
    val sakInformasjon: SakInformasjon? = null,
    val saktype: SakType? = null
)