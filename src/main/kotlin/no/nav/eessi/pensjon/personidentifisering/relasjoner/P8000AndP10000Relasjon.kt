package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.BARN
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.ETTERLATTE
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.FORSORGER
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer


class P8000AndP10000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String)  :T2000TurboRelasjon(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val fnrListe = mutableListOf<SEDPersonRelasjon>()
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        val personPin = Fodselsnummer.fra(forsikretPerson?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val personFdato = mapFdatoTilLocalDate(forsikretPerson?.foedselsdato)
        val sokForsikretKriterie = forsikretPerson?.let { opprettSokKriterie(it) }

        //kap.2 forsikret person
        fnrListe.add(SEDPersonRelasjon(personPin, Relasjon.FORSIKRET, sedType = sed.type, sokKriterier = sokForsikretKriterie, fdato = personFdato, rinaDocumentId = rinaDocumentId))
        logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")

        //Annenperson søker/barn o.l
        val annenPerson = sed.nav?.annenperson?.person
        val annenPersonPin = Fodselsnummer.fra(annenPerson?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val annenPersonFdato = mapFdatoTilLocalDate(annenPerson?.foedselsdato)
        val rolle = annenPerson?.rolle

        annenPerson?.let { person ->
            if (bucType == BucType.P_BUC_05 || bucType == BucType.P_BUC_10 || bucType == BucType.P_BUC_02) {
                val sokAnnenPersonKriterie =  opprettSokKriterie(person)
                val annenPersonRelasjon = when (rolle) {
                    ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                    FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato,rinaDocumentId = rinaDocumentId)
                    BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                    else -> null
                }

                annenPersonRelasjon?.let { annenRelasjon ->
                    fnrListe.add(annenRelasjon)
                    logger.debug("Legger til person med relasjon: ${annenPersonRelasjon.relasjon}, sokForsikret: ${sokAnnenPersonKriterie != null}")
                }
            }
        }

        return fnrListe
    }

}