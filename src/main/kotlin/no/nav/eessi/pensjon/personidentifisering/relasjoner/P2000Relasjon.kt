package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class P2000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : AbstractRelasjon( sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> = hentForsikretPerson(bestemSaktype(bucType))

}