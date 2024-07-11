package no.nav.eessi.pensjon.journalforing.journalpost

data class FerdigstillJournalPost (
    val journalfoerendeEnhet: String? = null,
    val journalfortAvNavn: String? = null,
    val opprettetAvNavn: String? = null,
    val datoJournal: String? = null,
    val datoSendtPrint: String? = null
)