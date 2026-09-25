package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.GJENLEVENDE
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson

abstract class GjenlevendeHvisFinnes(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : AbstractRelasjon(sed, bucType, rinaDocumentId) {

    fun hentRelasjonGjenlevendeFnrHvisFinnes(gjenlevendeBruker: Bruker? = null) : List<SEDPersonRelasjon> {
        logger.info("hentRelasjonGjenlevendeFnrHvisFinnes Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}, med rinasak: $rinaDocumentId")

        val sedType = sed.type
        //gjenlevendePerson (søker)
        val gjenlevendePerson = gjenlevendeBruker?.person
        secureLog.info("Hva er gjenlevendePerson pin?: ${gjenlevendePerson?.pin}")

        if (gjenlevendePerson == null) {
            return emptyList()
        }

        val gjenlevendePin = Fodselsnummer.fra(gjenlevendePerson.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val gjenlevendeFdato = mapFdatoTilLocalDate(gjenlevendePerson.foedselsdato)
        val sokPersonKriterie =  opprettSokKriterie(gjenlevendePerson)

        val gjenlevendeRelasjon = gjenlevendePerson.relasjontilavdod?.relasjon

        logger.info("Innhenting av relasjon: ${gjenlevendeRelasjon?.let { RelasjonTilAvdod.values().firstOrNull{it.kode == gjenlevendeRelasjon}}}")

        if (gjenlevendeRelasjon == null) {
            secureLog.info("Legger til person $GJENLEVENDE med ukjente relasjoner")
            return listOf(SEDPersonRelasjon(gjenlevendePin, GJENLEVENDE, sedType = sedType, sokKriterier = sokPersonKriterie, fdato = gjenlevendeFdato, rinaDocumentId = rinaDocumentId))
        }

        val sakType =  if (erGjenlevendeBarn(gjenlevendeRelasjon)) {
            BARNEP
        } else {
            GJENLEV
        }
        secureLog.info("Legger til person $GJENLEVENDE med sakType: $sakType")
        return listOf(SEDPersonRelasjon(gjenlevendePin, GJENLEVENDE, sakType, sedType = sedType, sokKriterier = sokPersonKriterie, gjenlevendeFdato, rinaDocumentId= rinaDocumentId))
    }

    fun erGjenlevendeBarn(relasjon: String): Boolean {
        val gyldigeBarneRelasjoner = listOf("EGET_BARN", "06", "ADOPTIVBARN", "07", "FOSTERBARN", "08", "STEBARN", "09")
        return relasjon in gyldigeBarneRelasjoner
    }


    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    fun leggTilAnnenGjenlevendeFnrHvisFinnes(): SEDPersonRelasjon? {
        val gjenlevendePerson = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }?.person
        secureLog.info("""leggTilAnnenGjenlevendeFnrHvisFinnes: $gjenlevendePerson
            | rolle: ${sed.nav?.annenperson?.person?.rolle}""".trimMargin())

        gjenlevendePerson?.let { person ->
            val sokPersonKriterie = opprettSokKriterie(person)
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato =  mapFdatoTilLocalDate(person.foedselsdato)
            return SEDPersonRelasjon(
                fodselnummer, GJENLEVENDE, sedType = sed.type, sokKriterier = sokPersonKriterie,
                fdato = fdato, rinaDocumentId = rinaDocumentId
            ).also {
                secureLog.info("""leggTilAnnenGjenlevendeFnrHvisFinnes: 
                    | SEDPersonRelasjon: ${it.toJson()}""".trimMargin()
                )
            }
        }
        return null
    }


}