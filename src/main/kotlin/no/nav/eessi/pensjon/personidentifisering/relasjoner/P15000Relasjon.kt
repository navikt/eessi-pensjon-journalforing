package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.SedTypeUtils.mapKravtypeTilSaktype
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon

class P15000Relasjon(private val sed: SED, bucType: BucType, rinaDocumentId: String) :
    GjenlevendeHvisFinnes( sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val kravType = sed.nav?.krav?.type
        val saksType = mapKravtypeTilSaktype(kravType)

        logger.info("${sed.type.name}, krav: ${kravType},  saktype: $saksType")

        return if (saksType == GJENLEV) {
            logger.debug("legger til gjenlevende: ($saksType)")
            hentRelasjonGjenlevendeFnrHvisFinnes((sed as P15000).pensjon?.gjenlevende)
        } else {
            logger.debug("legger til forsikret: ($saksType)")
            hentForsikretPerson(saksType)
        }

    }

}