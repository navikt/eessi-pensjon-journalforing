package no.nav.eessi.pensjon.journalforing.saf

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import no.nav.eessi.pensjon.journalforing.Bruker

data class OppdaterJournalpost(
    val journalpostId: String,
    val dokumenter: List<SafDokument>,
    val sak: SafSak?,
    val bruker: Bruker?,
)

data class SafDokument(
    val dokumentInfoId: String,
    val tittel: String,
    val brevkode: String?
)

data class SafSak(
    val fagsakId: String?,
    val sakstype: String? = null,
    val tema: String? = null,
    val fagsaksystem: String? = null,
    val arkivsaksnummer: String? = null,
    val arkivsaksystem: String? = null,
)

enum class SafJournalposttype {
    I, U, N,

    @JsonEnumDefaultValue
    UKJENT;
}

enum class SafJournalstatus(
    val erJournalfoert: Boolean
) {
    JOURNALFOERT(true),
    FERDIGSTILT(true),
    MOTTATT(false),
    UNDER_ARBEID(false),
    FEILREGISTRERT(false),
    EKSPEDERT(false),
    AVBRUTT(false),
    UTGAAR(false),
    UKJENT_BRUKER(false),
    RESERVERT(false),
    OPPLASTING_DOKUMENT(false),

    @JsonEnumDefaultValue
    UKJENT(false);
}