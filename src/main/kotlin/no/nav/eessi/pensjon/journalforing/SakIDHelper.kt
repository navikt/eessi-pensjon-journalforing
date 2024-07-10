package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon

object SakIDHelper {
    fun gjennySak(gjennySak: GjennySak?): Sak? {
        return if (gjennySak != null) Sak("FAGSAK", gjennySak.sakId, "EY") else null
    }

    fun sakIdFraSed(sakIdFraSed: String?): Sak? {
        return if (!sakIdFraSed.isNullOrBlank()) Sak("FAGSAK", sakIdFraSed, "EY") else null
    }

    fun sakInformasjon(sakInformasjon: SakInformasjon?): Sak? {
        return if (!sakInformasjon?.sakId.isNullOrBlank()) Sak("FAGSAK", sakInformasjon?.sakId!!, "EY") else null
    }
}
