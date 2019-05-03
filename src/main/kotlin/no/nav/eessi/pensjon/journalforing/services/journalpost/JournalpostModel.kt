package no.nav.eessi.pensjon.journalforing.services.journalpost

data class JournalpostModel(
    val avsenderMottaker: AvsenderMottaker,
    val behandlingstema: String? = null, //optional
    val bruker: Bruker,
    val dokumenter: List<Dokumenter>,
    val eksternReferanseId: String? = null, //optional
    val journalfoerendeEnhet: Int? = null, //optional
    val journalpostType: String,
    val kanal: String = "EESSI",
    val sak: Sak,
    val tema: String,
    val tilleggsopplysninger: List<Tilleggsopplysninger>? = null, //optional
    val tittel: String
)

data class Dokumenter(
    val brevkode: String? = null, //optional
    val dokumentKategori: String? = null, //optional
    val dokumentvarianter: List<Dokumentvarianter>,
    val tittel: String? = null
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
    val id: String? = null, //optional
    val land: String? = null, //optional
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