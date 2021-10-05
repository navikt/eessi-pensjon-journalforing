package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon

class RelasjonsHandler {

    fun hentRelasjoner(seder: List<Pair<String, SED>>, bucType: BucType): List<SEDPersonRelasjon> {
        val fnrListe = mutableSetOf<SEDPersonRelasjon>()
        val sedMedForsikretPrioritet = listOf(SedType.H121, SedType.H120, SedType.H070)

        seder.forEach { (rinaDocumentId,sed) ->
            try {
                getRelasjonHandler(sed, bucType, rinaDocumentId)?.let { handler ->
                    fnrListe.addAll(handler.hentRelasjoner())
                }

            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
        }

        val resultat = fnrListe
            .filter { it.erGyldig() || it.sedType in sedMedForsikretPrioritet }
            .filterNot { it.filterUbrukeligeElemeterAvSedPersonRelasjon() }
            .sortedBy { it.relasjon }

        return resultat.ifEmpty { fnrListe.distinctBy { it.fnr } }

    }

    private fun getRelasjonHandler(sed: SED, bucType: BucType, rinaDocumentId: String): T2000TurboRelasjon? {

        if (sed.type.kanInneholdeIdentEllerFdato()) {
            return when (sed.type) {
                SedType.R005 -> R005Relasjon(sed, bucType,rinaDocumentId)
                SedType.P2000 -> P2000Relasjon(sed, bucType,rinaDocumentId)
                SedType.P2200 -> P2000Relasjon(sed, bucType,rinaDocumentId)
                SedType.P2100 -> P2100Relasjon(sed, bucType,rinaDocumentId)
                SedType.P8000, SedType.P10000 -> P8000AndP10000Relasjon(sed, bucType,rinaDocumentId)
                SedType.P6000 -> P6000Relasjon(sed, bucType,rinaDocumentId)
                SedType.P15000 -> P15000Relasjon(sed, bucType,rinaDocumentId)

                SedType.H070, SedType.H120, SedType.H121 -> GenericRelasjon(sed, bucType, rinaDocumentId)
                else -> GenericRelasjon(sed, bucType,rinaDocumentId)
            }
        }
        return null
    }
}
