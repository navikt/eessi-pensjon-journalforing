package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class P2200Relasjon(sed: SED, private val bucType: BucType, val rinaDocumentId: String) : AbstractRelasjon(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> = hentForsikretPerson(bestemSaktype(bucType))

}