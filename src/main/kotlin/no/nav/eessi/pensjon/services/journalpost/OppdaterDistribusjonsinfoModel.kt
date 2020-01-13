package no.nav.eessi.pensjon.services.journalpost

import no.nav.eessi.pensjon.json.mapAnyToJson

/**
 *  https://confluence.adeo.no/display/BOA/oppdaterDistribusjonsinfo
 *
 *  /rest/journalpostapi/v1/journalpost/{journalpostId}/oppdaterDistribusjonsinfo
 *
 *  Fullfører journalføringen og låser journalposten for senere endringer
 */
class OppdaterDistribusjonsinfoRequest {
    val settStatusEkspedert: String = "true"
    val utsendingsKanal: String = "EESSI"

    override fun toString(): String {
        return mapAnyToJson(this,true)
    }
}
