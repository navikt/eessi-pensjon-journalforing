package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.eux.model.buc.BucType

class P12000Relasjon(private val sed: SED, bucType: BucType, rinaDocumentId: String) :
    GjenlevendeHvisFinnes(sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> = hentRelasjonGjenlevendeFnrHvisFinnes(sed.pensjon?.gjenlevende)

}