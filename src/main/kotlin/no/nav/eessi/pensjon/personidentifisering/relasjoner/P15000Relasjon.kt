package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon

class P15000Relasjon(private val sed: SED, bucType: BucType, rinaDocumentId: String) :
    GjenlevendeHvisFinnes( sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val sedKravString = sed.nav?.krav?.type
        val saktype = if (sedKravString == null) null else mapKravtypeTilSaktype(sedKravString)

        logger.info("${sed.type.name}, krav: $sedKravString,  saktype: $saktype")

        return if (saktype == GJENLEV) {
            logger.debug("legger til gjenlevende: ($saktype)")
            hentRelasjonGjenlevendeFnrHvisFinnes((sed as P15000).p15000Pensjon?.gjenlevende)
        } else {
            logger.debug("legger til forsikret: ($saktype)")
            hentForsikretPerson(saktype)
        }

    }

    private fun mapKravtypeTilSaktype(krav: String?): SakType {
        return when (krav) {
            "02" -> GJENLEV
            "03" -> UFOREP
            else -> ALDER
        }
    }


}