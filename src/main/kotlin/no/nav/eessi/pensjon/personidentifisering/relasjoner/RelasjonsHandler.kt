package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object RelasjonsHandler {

    private val logger: Logger = LoggerFactory.getLogger(RelasjonsHandler::class.java)

    fun hentRelasjoner(seder: List<Pair<String, SED>>, bucType: BucType): List<SEDPersonRelasjon> {
        val fnrListe = mutableSetOf<SEDPersonRelasjon>()

        seder.forEach { (rinaDocumentId,sed) ->
            try {
                getRelasjonHandler(sed, bucType, rinaDocumentId)?.let { handler ->
                    fnrListe.addAll(handler.hentRelasjoner())
                }

            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
        }

        return filterRelasjoner(fnrListe.toList())

    }

    private fun filterRelasjoner(relasjonList: List<SEDPersonRelasjon>): List<SEDPersonRelasjon> {
         logger.info("*** Filterer relasjonListe, samme oppføringer, ugyldige verdier o.l")

        relasjonList.onEach { logger.debug("$it") }

        //filterering av relasjoner med kjent fnr
        val relasjonerMedFnr = relasjonList.filter { it.fnr != null }.distinctBy { it.fnr }
        //filtering av relasjoner uten kjent fnr
        val relasjonerUtenFnr = relasjonList.filter { it.fnr == null }

        return (relasjonerMedFnr + relasjonerUtenFnr).also {
            if(it.size < relasjonList.size){
                logger.warn("Det gjenstår ${it.size} relasjoner etter filtrering")
            }
        }
    }

    private fun getRelasjonHandler(sed: SED, bucType: BucType, rinaDocumentId: String): AbstractRelasjon? {

        if (sed.type.kanInneholdeIdentEllerFdato()) {
            return when (sed.type) {
                //R005 SED eneste vi leter etter fnr for R_BUC_02
                SEDTYPE_R005 -> R005Relasjon(sed, bucType,rinaDocumentId)

                //Øvrige P-SED vi støtter for innhenting av FNR
                SEDTYPE_P2000 -> P2000Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P2200 -> P2200Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P2100 -> P2100Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P4000 -> P4000Relasjon(sed, bucType, rinaDocumentId)
                SEDTYPE_P5000 -> P5000Relasjon(sed, bucType, rinaDocumentId)
                SEDTYPE_P6000 -> P6000Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P8000 -> P8000AndP10000Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P10000 -> P8000AndP10000Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P15000 -> P15000Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P11000 -> P11000Relasjon(sed, bucType,rinaDocumentId)
                SEDTYPE_P12000 -> P12000Relasjon(sed, bucType,rinaDocumentId)

                //H-SED vi støtter for innhenting av fnr kun for forsikret
                SEDTYPE_H070, SEDTYPE_H120, SEDTYPE_H121 -> GenericRelasjon(sed, bucType, rinaDocumentId)

                //resterende gyldige sed med fnr kommer hit.. (P9000, P3000, P4000.. osv.)
                else -> GenericRelasjon(sed, bucType,rinaDocumentId)
            }
        }
        logger.debug("Her kommer vi dersom getRelasjonsHandler returnerer null")
        return null
    }
}
