package no.nav.eessi.pensjon.journalforing.saf

import no.nav.eessi.pensjon.utils.mapAnyToJson

/**
 * Request og responsemodell for SAF GraphQL tjeneste
 * se https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning
 */

data class SafRequest(
    val journalpostId: String,
    val query: String = """
        query {journalpost(journalpostId:  "$journalpostId") {              
                  journalpostId
                  bruker {
                    id
                    type
                  }  
                  tittel
                  journalposttype
                  journalstatus
                  tema
                  behandlingstema
                  journalforendeEnhet
                  eksternReferanseId
                  tilleggsopplysninger {
                    nokkel
                    verdi
                  }
                  datoOpprettet
                }
        }""".trimIndent()
) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}
data class Journalpost(
    val journalpostId: String,
    val bruker: Bruker,
    val tittel: String?,
    val journalposttype: String?,
    val journalstatus: String?,
    val tema: String,
    val behandlingstema: String,
    val behandlingstemanavn: String,
    val journalforendeEnhet: String,
    val eksternReferanseId: String,
    val tilleggsopplysninger: List<Map<String, String>>,
    val datoOpprettet: String,
)

data class HentJournalPoster (val data: Data) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}

data class Data(val journalpost: Journalpost)

data class Bruker(
    val id: String,
    val type: BrukerIdType
)

enum class BrukerIdType {
    FNR,
    AKTOERID
}

/**
 * https://confluence.adeo.no/display/BOA/Enum%3A+Variantformat
 */
enum class VariantFormat {
    ARKIV,
    FULLVERSJON,
    PRODUKSJON,
    PRODUKSJON_DLF,
    SLADDET,
    ORIGINAL
}




