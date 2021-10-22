package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

/**
 * Generic Hjelpe Relasjon klassse for innhenting av ident fra øvrikge SED vi ikke har spesefikkt laget egne klasser for.
 *
 */
class GenericRelasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) : AbstractRelasjon(sed,bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val fnrListe = mutableListOf<SEDPersonRelasjon>()

        leggTilAnnenGjenlevendeFnrHvisFinnes()?.let { annenRelasjon ->
            fnrListe.add(annenRelasjon)
        }
        fnrListe.addAll(hentForsikretPerson(bestemSaktype(bucType)))

        return fnrListe
    }



    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(): SEDPersonRelasjon? {
        val gjenlevendePerson = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }?.person

        gjenlevendePerson?.let { person ->
            val sokPersonKriterie = opprettSokKriterie(person)
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato =  mapFdatoTilLocalDate(person.foedselsdato)
            return SEDPersonRelasjon(fodselnummer, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokPersonKriterie, fdato = fdato, rinaDocumentId=rinaDocumentId)
        }
        return null
    }


}