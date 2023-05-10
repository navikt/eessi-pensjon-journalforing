package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.sed.P4000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon

class P4000Relasjon(private val sed: SED, private val bucType: BucType, val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val forsikret = hentForsikretPerson(bestemSaktype(bucType))
        val gjenlevende = hentRelasjonGjenlevendeFnrHvisFinnes((sed as P4000).p4000Pensjon?.gjenlevende)

        return gjenlevende.ifEmpty { forsikret }

    }

}