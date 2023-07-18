package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer

/**
 * Generic Hjelpe Relasjon klassse for innhenting av ident fra øvrikge SED vi ikke har spesefikkt laget egne klasser for.
 *
 */
class GenericRelasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed,bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val fnrListe = mutableListOf<SEDPersonRelasjon>()

        leggTilAnnenGjenlevendeFnrHvisFinnes()?.let { annenRelasjon ->
            fnrListe.add(annenRelasjon)
        }
        if(bucType == P_BUC_02) {
            fnrListe.addAll(hentRelasjonGjenlevendeFnrHvisFinnes())
        } else {
            fnrListe.addAll(hentForsikretPerson(bestemSaktype(bucType)))
        }
        return fnrListe
    }
}