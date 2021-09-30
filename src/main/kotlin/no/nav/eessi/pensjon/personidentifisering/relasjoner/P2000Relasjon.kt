package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class P2000Relasjon(private val sed: SED, private val bucType: BucType) : T2000TurboRelasjon(sed, bucType) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {

            forsikretbruker?.let {  person ->
                val sokPersonKriterie =  opprettSokKriterie(person)
                val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                val fdato = mapFdatoTilLocalDate(person.foedselsdato)

                logger.debug("Legger til person ${Relasjon.FORSIKRET} og sedType: ${sed.type}")
                return listOf(SEDPersonRelasjon(fodselnummer, Relasjon.FORSIKRET, null, sed.type, sokPersonKriterie, fdato))
            }
        logger.warn("Ingen forsikret person funnet")
        return emptyList()
    }

}