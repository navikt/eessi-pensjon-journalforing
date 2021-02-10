package no.nav.eessi.pensjon.pdf

class JournalPostDokument(
        val brevkode: String? = null,
        val dokumentKategori: String? = "SED",
        val dokumentvarianter: List<Dokumentvarianter>, //REQUIRED
        val tittel: String? = null
)

class Dokumentvarianter(
    val filtype: String, //REQUIRED
    val fysiskDokument: String, //REQUIRED
    val variantformat: Variantformat //REQUIRED
)

enum class Variantformat {
    ARKIV
}
