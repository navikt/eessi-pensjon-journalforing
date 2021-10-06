package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

abstract class GjenlevendeHvisFinnes(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : AbstractRelasjon(sed, bucType, rinaDocumentId) {

    fun hentRelasjonGjenlevendeFnrHvisFinnes(gjenlevendeBruker: Bruker? = null, saktype: Saktype? = null) : List<SEDPersonRelasjon> {
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        val fnrListe = mutableListOf<SEDPersonRelasjon>()
        val sedType = sed.type

        //forsikretPerson (avdød eller søker)
        forsikretPerson?.let { person ->
            val forsikretPersonKriterie = opprettSokKriterie(person)
            val forsikretFnr = Fodselsnummer.fra(forsikretPerson?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato = mapFdatoTilLocalDate(forsikretPerson?.foedselsdato)

            fnrListe.add(SEDPersonRelasjon(forsikretFnr, Relasjon.FORSIKRET, saktype, sedType, fdato = fdato, sokKriterier = forsikretPersonKriterie, rinaDocumentId = rinaDocumentId))
            logger.debug("Legger til forsikret-person ${Relasjon.FORSIKRET}")
        }

        //gjenlevendePerson (søker)
        val gjenlevendePerson = gjenlevendeBruker?.person
        logger.debug("Hva er gjenlevendePerson?: ${gjenlevendePerson?.pin}")
        gjenlevendePerson?.let { gjenlevendePerson ->
            val gjenlevendePin = Fodselsnummer.fra(gjenlevendePerson.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val gjenlevendeFdato = mapFdatoTilLocalDate(gjenlevendePerson.foedselsdato)
            val sokPersonKriterie =  opprettSokKriterie(gjenlevendePerson)

            val gjenlevendeRelasjon = gjenlevendePerson.relasjontilavdod?.relasjon

            if (gjenlevendeRelasjon == null) {
                fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sedType, sokKriterier = sokPersonKriterie, fdato = gjenlevendeFdato, rinaDocumentId = rinaDocumentId))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return fnrListe
            }

            val sakType =  if (erGjenlevendeBarn(gjenlevendeRelasjon)) {
                Saktype.BARNEP
            } else {
                Saktype.GJENLEV
            }
            fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sakType, sedType = sedType, sokKriterier = sokPersonKriterie, gjenlevendeFdato, rinaDocumentId= rinaDocumentId))
            logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med sakType: $sakType")
        }

        return fnrListe
    }

    fun erGjenlevendeBarn(relasjon: String): Boolean {
        return (relasjon == "EGET_BARN" ||
                relasjon =="ADOPTIVBARN" ||
                relasjon == "FOSTERBARN" ||
                relasjon =="STEBARN" )
    }


}