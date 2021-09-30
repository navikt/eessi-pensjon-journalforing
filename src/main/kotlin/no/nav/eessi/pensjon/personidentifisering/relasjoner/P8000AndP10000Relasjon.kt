package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer


class P8000AndP10000Relasjon(private val sed: SED, private val bucType: BucType)  :T2000TurboRelasjon(sed, bucType) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {

        val fnrListe = mutableListOf<SEDPersonRelasjon>()
        logger.debug("Leter i ${sed.type}")

        val personPin = Fodselsnummer.fra(forsikretbruker?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val personFdato = mapFdatoTilLocalDate(forsikretbruker?.foedselsdato)

        val annenPerson = sed.nav?.annenperson?.person
        val annenPersonPin = Fodselsnummer.fra(annenPerson?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val annenPersonFdato = mapFdatoTilLocalDate(annenPerson?.foedselsdato)

        val rolle = annenPerson?.rolle

        val sokForsikretKriterie = forsikretbruker?.let { opprettSokKriterie(it) }

        logger.debug("Personpin: $personPin, AnnenPersonpin $annenPersonPin, Annenperson rolle : $rolle, sokForsikret: ${sokForsikretKriterie != null}")

        //kap.2 forsikret person
        fnrListe.add(SEDPersonRelasjon(personPin, Relasjon.FORSIKRET, sedType = sed.type, sokKriterier = sokForsikretKriterie, fdato = personFdato))
        logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")

        //Annenperson søker/barn o.l
        if (bucType == BucType.P_BUC_05 || bucType == BucType.P_BUC_10 || bucType == BucType.P_BUC_02) {
            val sokAnnenPersonKriterie = annenPerson?.let { opprettSokKriterie(it) }
            val annenPersonRelasjon = when (rolle) {
                Rolle.ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato)
                Rolle.FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato)
                Rolle.BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato)
                else -> SEDPersonRelasjon(annenPersonPin, Relasjon.ANNET, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato)
            }

            fnrListe.add(annenPersonRelasjon)
            logger.debug("Legger til person med relasjon: ${annenPersonRelasjon.relasjon}, sokForsikret: ${sokAnnenPersonKriterie != null}")
        }

        return fnrListe
    }

}