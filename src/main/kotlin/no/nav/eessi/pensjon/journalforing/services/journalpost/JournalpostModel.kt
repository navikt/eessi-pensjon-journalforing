package no.nav.eessi.pensjon.journalforing.services.journalpost

data class JournalpostModel(
    val avsenderMottaker: AvsenderMottaker,
    val behandlingstema: String?, //optional
    val bruker: Bruker,
    val dokumenter: List<Dokumenter>,
    val eksternReferanseId: String?, //optional
    val journalfoerendeEnhet: Int?, //optional
    val journalpostType: String,
    val kanal: String = "EESSI",
    val sak: Sak,
    val tema: String,
    val tilleggsopplysninger: List<Tilleggsopplysninger>?, //optional
    val tittel: String
)

data class Dokumenter(
    val brevkode: String?, //optional
    val dokumentKategori: String?, //optional
    val dokumentvarianter: List<Dokumentvarianter>,
    val tittel: String?
)

data class Dokumentvarianter(
    val filtype: String = "PDF/A",
    val fysiskDokument: String,
    val variantformat: String = "ARKIV"
)

data class Sak(
    val arkivsaksnummer: String,
    val arkivsaksystem: String
)

data class AvsenderMottaker(
    val id: String?, //optional
    val land: String?, //optional
    val navn: String
)

data class Tilleggsopplysninger(
    val nokkel: String,
    val verdi: String
)

data class Bruker(
    val id: String,
    val idType: String
)