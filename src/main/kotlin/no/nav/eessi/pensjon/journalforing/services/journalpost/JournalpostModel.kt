package no.nav.eessi.pensjon.journalforing.services.journalpost

data class JournalpostModel(
    val avsenderMottaker: AvsenderMottaker? = null,
    val behandlingstema: String? = null,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>, //REQUIRED
    val eksternReferanseId: String? = null,
    val journalfoerendeEnhet: String? = null,
    val journalpostType: String = "UTGAAENDE", //REQUIRED
    val kanal: String? = null,
    val sak: Sak? = null,
    val tema: String = "PEN", //REQUIRED
    val tilleggsopplysninger: List<Tilleggsopplysninger>? = null,
    val tittel: String //REQUIRED
)

data class Dokument(
    val brevkode: String? = null,
    val dokumentKategori: String? = "SED",
    val dokumentvarianter: List<Dokumentvarianter>, //REQUIRED
    val tittel: String? = null
)

data class Dokumentvarianter(
    val filtype: String = "PDF/A", //REQUIRED
    val fysiskDokument: String, //REQUIRED
    val variantformat: String = "ARKIV" //REQUIRED
)

data class Sak(
    val arkivsaksnummer: String, //REQUIRED
    val arkivsaksystem: String //REQUIRED
)

data class AvsenderMottaker(
    val id: String? = null,
    val land: String? = null,
    val navn: String //REQUIRED
)

data class Tilleggsopplysninger(
    val nokkel: String, //REQUIRED
    val verdi: String //REQUIRED
)

data class Bruker(
    val id: String, //REQUIRED
    val idType: String = "FNR" //REQUIRED
)