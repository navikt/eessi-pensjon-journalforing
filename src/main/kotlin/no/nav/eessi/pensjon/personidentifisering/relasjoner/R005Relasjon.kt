package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class R005Relasjon(private val sed: SED, private val bucType: BucType, val rinaDocumentId: String) : AbstractRelasjon(sed, bucType,rinaDocumentId) {
    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        return filterPinPersonR005(sed as R005)
    }

    private fun filterPinPersonR005(sed: R005): List<SEDPersonRelasjon> {
        return sed.recoveryNav?.brukere
            ?.mapNotNull { bruker ->
                val relasjon = mapRBUC02Relasjon(bruker.tilbakekreving?.status?.type)
                val fdato = mapFdatoTilLocalDate(bruker.person?.foedselsdato)
                if(relasjon != Relasjon.ANNET){
                    Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                        ?.let { SEDPersonRelasjon(it, relasjon, sedType = sed.type, fdato = fdato, rinaDocumentId = rinaDocumentId) }
                } else {
                    null
                }
            } ?: emptyList()
    }

    private fun mapRBUC02Relasjon(type: String?): Relasjon {
        return when (type) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }
}
