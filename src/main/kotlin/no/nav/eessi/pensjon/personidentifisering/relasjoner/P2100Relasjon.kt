package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon

class P2100Relasjon(private val sed: SED, bucType: BucType, rinaDocumentId: String) :
    GjenlevendeHvisFinnes( sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> = hentRelasjonGjenlevendeFnrHvisFinnes(sed.pensjon?.gjenlevende)

}