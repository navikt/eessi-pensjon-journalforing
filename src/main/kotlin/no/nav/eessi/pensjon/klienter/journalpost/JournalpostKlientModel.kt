package no.nav.eessi.pensjon.klienter.journalpost

open class JournalpostKlientModel (

    val rinaSakId: String,
    val fnr: String?,
    val personNavn: String?,
    val bucType: String,
    val sedType: String,
    val sedHendelseType: String,
    val eksternReferanseId: String?,
    val kanal: String?,
    val journalfoerendeEnhet: String?,
    val arkivsaksnummer: String?,
    val dokumenter: String,
    val forsokFerdigstill: Boolean? = false,
    val avsenderLand: String?,
    val avsenderNavn: String?,
    val ytelseType: String?)