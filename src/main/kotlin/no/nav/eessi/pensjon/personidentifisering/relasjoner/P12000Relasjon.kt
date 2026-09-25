package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.sed.P12000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny

/**
 * Regler for uthenting av relasjoner for P12000
 * Henter person 2 (annen person) om den er tilgjengelig
 * ellers benyttes person 1 (forsikter person)
 */
class P12000Relasjon(val sed: SED, val bucType: BucType, val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed, bucType,rinaDocumentId) {


    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val forsikret = hentForsikretPerson(bestemSaktype(bucType))
        val p12000 = mapJsonToAny<P12000>( mapAnyToJson(sed))
        val gjenlevende = hentRelasjonGjenlevendeFnrHvisFinnes(p12000.pensjonP12000?.gjenlevende?.person)
    
        return gjenlevende.ifEmpty { forsikret }
    }

}