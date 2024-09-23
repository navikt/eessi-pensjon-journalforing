package no.nav.eessi.pensjon.journalforing.journalpost

import no.nav.eessi.pensjon.journalforing.Bruker
import no.nav.eessi.pensjon.models.Tema
import java.util.*

abstract class OpprettJournalpostRequestBase(
    open val tema: Tema? = null,
    open val bruker: Bruker? = null,
    open val dokumenter: String? = null,
    ) {
    val kanal: String = "EESSI"
    val eksternReferanseId: String = UUID.randomUUID().toString()

}