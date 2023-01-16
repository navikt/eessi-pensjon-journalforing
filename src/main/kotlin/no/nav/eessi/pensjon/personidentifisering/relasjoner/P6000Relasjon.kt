package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class P6000Relasjon(private val sed: SED,
                    private val bucType: BucType,
                    val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed,bucType,rinaDocumentId) {

        override fun hentRelasjoner(): List<SEDPersonRelasjon> {
            val forsikret = hentForsikretPerson(bestemSaktype(bucType))
            val gjenlevende =  hentRelasjonGjenlevendeFnrHvisFinnes((sed as P6000).p6000Pensjon?.gjenlevende)

            return gjenlevende.ifEmpty { forsikret }

        }

    }