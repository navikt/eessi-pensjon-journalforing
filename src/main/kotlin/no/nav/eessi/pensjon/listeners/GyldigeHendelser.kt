package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse

class GyldigeHendelser {
    companion object {
        private const val GYLDIG_SEKTOR_KODE = "P"

        val gyldigeInnkommendeBucTyper = listOf(H_BUC_07, R_BUC_02, M_BUC_02, M_BUC_03a, M_BUC_03b)
        val gyldigUtgaaendeBucType = listOf(H_BUC_07, R_BUC_02, M_BUC_02, M_BUC_03a, M_BUC_03b)

        fun mottatt(hendelse: SedHendelse) =
                when {
                    hendelse.bucType == null -> false
                    hendelse.bucType in gyldigeInnkommendeBucTyper -> true
                    hendelse.sektorKode == GYLDIG_SEKTOR_KODE -> true
                    else -> false
                }

        fun sendt(hendelse: SedHendelse) =
                when {
                    hendelse.bucType == null -> false
                    hendelse.bucType in gyldigUtgaaendeBucType -> true
                    hendelse.sektorKode == GYLDIG_SEKTOR_KODE -> true
                    else -> false
                }
    }
}
