package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.sed.SedHendelseModel

class GyldigeHendelser {
    companion object {
        private const val gyldigSektorKode = "P"

        private val gyldigeInnkommendeBucTyper = listOf(BucType.H_BUC_07, BucType.R_BUC_02)
        private val gyldigUtgaaendeBucType = BucType.R_BUC_02

        fun mottatt(hendelse: SedHendelseModel) =
                when {
                    hendelse.bucType == null -> false
                    hendelse.bucType in gyldigeInnkommendeBucTyper || gyldigSektorKode == hendelse.sektorKode -> true
                    else -> false
                }

        fun sendt(hendelse: SedHendelseModel) =
                when {
                    hendelse.bucType == null -> false
                    gyldigUtgaaendeBucType == hendelse.bucType || gyldigSektorKode == hendelse.sektorKode -> true
                    else -> false
                }
    }
}
