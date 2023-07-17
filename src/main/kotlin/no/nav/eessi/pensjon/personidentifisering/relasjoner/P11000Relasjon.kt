package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon

/**
 * Regler for uthenting av relasjoner for P12000
 * Henter person 2 (annen person) om den er tilgjengelig
 * ellers benyttes person 1 (forsikter person)
 */
class P11000Relasjon(val sed: SED, val bucType: BucType, val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed, bucType,rinaDocumentId) {


    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val forsikret = hentForsikretPerson(bestemSaktype(bucType))
        val gjenlevende = hentRelasjonGjenlevendeFnrHvisFinnes(sed.pensjon?.gjenlevende)

        return gjenlevende.ifEmpty { forsikret }
    }

}