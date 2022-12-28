package no.nav.eessi.pensjon.klienter.journalpost

import no.nav.eessi.pensjon.utils.mapAnyToJson

/**
 *  https://confluence.adeo.no/display/BOA/oppdaterDistribusjonsinfo
 *
 *  /rest/journalpostapi/v1/journalpost/{journalpostId}/oppdaterDistribusjonsinfo
 *
 *  Fullfører journalføringen og låser journalposten for senere endringer
 */
class OppdaterDistribusjonsinfoRequest {
    val settStatusEkspedert: Boolean = true
    val utsendingsKanal: String = "EESSI"

    override fun toString(): String {
        return mapAnyToJson(this,true)
    }
}
