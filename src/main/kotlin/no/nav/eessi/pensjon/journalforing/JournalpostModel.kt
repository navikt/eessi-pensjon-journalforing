package no.nav.eessi.pensjon.journalforing

class JournalpostModel {

    sealed class FerdigJournalpost {
        abstract val description: String
        abstract val metricTagValueOverride: String?

        val metricTagValue: String
            get() = metricTagValueOverride ?: description
    }

    data class Ferdigstilt(override val description: String, override val metricTagValueOverride: String? = null): FerdigJournalpost()
    data class IngenFerdigstilling(override val description: String, override val metricTagValueOverride: String? = null): FerdigJournalpost()
}