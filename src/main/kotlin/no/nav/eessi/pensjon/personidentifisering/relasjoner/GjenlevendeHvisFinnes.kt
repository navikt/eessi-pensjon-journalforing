package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.GJENLEVENDE
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer

abstract class GjenlevendeHvisFinnes(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : AbstractRelasjon(sed, bucType, rinaDocumentId) {

    fun hentRelasjonGjenlevendeFnrHvisFinnes(gjenlevendeBruker: Bruker? = null) : List<SEDPersonRelasjon> {
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        val sedType = sed.type
        //gjenlevendePerson (søker)
        val gjenlevendePerson = gjenlevendeBruker?.person
        logger.debug("Hva er gjenlevendePerson pin?: ${gjenlevendePerson?.pin}")

        if (gjenlevendePerson == null) {
            return emptyList()
        }

        val gjenlevendePin = Fodselsnummer.fra(gjenlevendePerson.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val gjenlevendeFdato = mapFdatoTilLocalDate(gjenlevendePerson.foedselsdato)
        val sokPersonKriterie =  opprettSokKriterie(gjenlevendePerson)

        val gjenlevendeRelasjon = gjenlevendePerson.relasjontilavdod?.relasjon
        logger.info("Innhenting av relasjon: $gjenlevendeRelasjon")

        if (gjenlevendeRelasjon == null) {
            logger.debug("Legger til person $GJENLEVENDE med ukjente relasjoner")
            return listOf(SEDPersonRelasjon(gjenlevendePin, GJENLEVENDE, sedType = sedType, sokKriterier = sokPersonKriterie, fdato = gjenlevendeFdato, rinaDocumentId = rinaDocumentId))
        }

        val sakType =  if (erGjenlevendeBarn(gjenlevendeRelasjon)) {
            BARNEP
        } else {
            GJENLEV
        }
        logger.debug("Legger til person $GJENLEVENDE med sakType: $sakType")
        return listOf(SEDPersonRelasjon(gjenlevendePin, GJENLEVENDE, sakType, sedType = sedType, sokKriterier = sokPersonKriterie, gjenlevendeFdato, rinaDocumentId= rinaDocumentId))
    }

    fun erGjenlevendeBarn(relasjon: String): Boolean {
        val gyldigeBarneRelasjoner = listOf("EGET_BARN", "06", "ADOPTIVBARN", "07", "FOSTERBARN", "08", "STEBARN", "09")
        return relasjon in gyldigeBarneRelasjoner
    }



}