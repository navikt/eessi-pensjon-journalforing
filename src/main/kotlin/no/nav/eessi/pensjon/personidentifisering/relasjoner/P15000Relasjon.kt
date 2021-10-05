package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class P15000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : GjenlevendeHvisFinnes( sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val sedKravString = sed.nav?.krav?.type
        val saktype = if (sedKravString == null) null else mapKravtypeTilSaktype(sedKravString)

        logger.info("${sed.type.name}, krav: $sedKravString,  saktype: $saktype")

        return if (saktype == Saktype.GJENLEV) {
            logger.debug("legger til gjenlevende: ($saktype)")
            hentRelasjonGjenlevendeFnrHvisFinnes((sed as P15000).p15000Pensjon?.gjenlevende, saktype)
        } else {
            logger.debug("legger til forsikret: ($saktype)")
            hentForsikretPerson(saktype)
        }

    }

    private fun mapKravtypeTilSaktype(krav: String?): Saktype {
        return when (krav) {
            "02" -> Saktype.GJENLEV
            "03" -> Saktype.UFOREP
            else -> Saktype.ALDER
        }
    }


}