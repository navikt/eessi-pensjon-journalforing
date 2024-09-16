package no.nav.eessi.pensjon.journalforing.journalpost

import no.nav.eessi.pensjon.journalforing.Bruker
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.utils.mapAnyToJson
import java.util.*

abstract class OpprettJournalpostRequestBase(
    open val tema: Tema? = null,
    open val bruker: Bruker? = null,

    ) {

    val kanal: String = "EESSI"
    val eksternReferanseId: String = UUID.randomUUID().toString()

    override fun toString(): String {
        return mapAnyToJson(this)
    }

}