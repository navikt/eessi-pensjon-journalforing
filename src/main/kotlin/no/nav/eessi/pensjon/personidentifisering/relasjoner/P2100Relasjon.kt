package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class P2100Relasjon(private val sed: SED, private val bucType: BucType) : GjenlevendeHvisFinnes(sed, bucType) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        return hentRelasjonGjenlevendeFnrHvisFinnes(sed.pensjon?.gjenlevende)
    }

}