package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class P5000Relasjon(private val sed: SED, private val bucType: BucType, val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val forsikret = hentForsikretPerson(bestemSaktype(bucType))
        val gjenlevende = hentRelasjonGjenlevendeFnrHvisFinnes((sed as P5000).p5000Pensjon?.gjenlevende, bestemSaktype(bucType))

        return gjenlevende.ifEmpty { forsikret }

    }

}